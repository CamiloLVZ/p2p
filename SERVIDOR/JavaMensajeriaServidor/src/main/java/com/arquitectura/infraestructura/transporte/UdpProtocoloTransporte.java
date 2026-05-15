package com.arquitectura.infraestructura.transporte;

import com.arquitectura.aplicacion.transferencia.GestorDescargasActivas;
import com.arquitectura.aplicacion.transferencia.StreamEmisorUdp;
import com.arquitectura.aplicacion.transferencia.StreamReceptorUdp;
import com.arquitectura.comun.dto.PaqueteDatos;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

/**
 * Transporte UDP.
 *
 * Detección por primer byte de cada datagrama:
 * - '{' (0x7B): JSON de control → flujo normal.
 * - 0x02: chunk de subida → StreamReceptorUdp.
 * - 0x03: señal de descarga → StreamEmisorUdp.
 */
public class UdpProtocoloTransporte implements ProtocoloTransporte {

    private static final Logger LOGGER = Logger.getLogger(UdpProtocoloTransporte.class.getName());

    static final byte STREAM_SIGNAL_UPLOAD   = 0x02;
    static final byte STREAM_SIGNAL_DOWNLOAD = 0x03;

    private static final int BUFFER_SIZE = 65535;

    private DatagramSocket socket;

    /** Hilo dedicado para I/O de streaming UDP — libera el loop de receive(). */
    private final ExecutorService streamingExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "udp-streaming");
        t.setDaemon(true);
        return t;
    });

    private final StreamReceptorUdp streamReceptor = new StreamReceptorUdp();
    private final StreamEmisorUdp   streamEmisor   = new StreamEmisorUdp();

    @Override
    public void iniciar(int puerto) {
        try {
            socket = new DatagramSocket(puerto);
        } catch (Exception e) {
            throw new RuntimeException("Error iniciando UDP en puerto " + puerto, e);
        }
    }

    @Override
    public void enviar(byte[] datos, String hostDestino, int puertoDestino) {
        try {
            InetAddress direccion = InetAddress.getByName(hostDestino);
            DatagramPacket paquete = new DatagramPacket(datos, datos.length, direccion, puertoDestino);
            socket.send(paquete);
        } catch (Exception e) {
            throw new RuntimeException("Error enviando datos por UDP", e);
        }
    }

    @Override
    public PaqueteDatos recibir() {
        while (true) {
            try {
                byte[] buffer = new byte[BUFFER_SIZE];
                DatagramPacket paquete = new DatagramPacket(buffer, buffer.length);
                socket.receive(paquete);

                if (paquete.getLength() == 0) continue;

                byte primerByte = buffer[0];

                // 0x01 = ACK de descarga enviado por el cliente
                // Lo depositamos en la cola para que StreamEmisorUdp lo consuma
                // sin competir por socket.receive()
                if (primerByte == 0x01 || primerByte == 0x00) {
                    byte[] ackData = new byte[paquete.getLength()];
                    System.arraycopy(buffer, 0, ackData, 0, paquete.getLength());
                    GestorDescargasActivas.getInstance().depositarAck(ackData);
                    continue;
                }

                if (primerByte == STREAM_SIGNAL_UPLOAD) {
                    int dataLen = paquete.getLength() - 1;
                    byte[] sinSenal = new byte[dataLen];
                    System.arraycopy(buffer, 1, sinSenal, 0, dataLen);
                    DatagramPacket chunkPkt = new DatagramPacket(
                            sinSenal, dataLen, paquete.getAddress(), paquete.getPort());
                    // Despachar en hilo separado — libera el loop para seguir recibiendo
                    streamingExecutor.submit(() ->
                            streamReceptor.procesarChunk(socket, chunkPkt));
                    continue;
                }

                if (primerByte == STREAM_SIGNAL_DOWNLOAD) {
                    int dataLen = paquete.getLength() - 1;
                    byte[] sinSenal = new byte[dataLen];
                    System.arraycopy(buffer, 1, sinSenal, 0, dataLen);
                    DatagramPacket descargaPkt = new DatagramPacket(
                            sinSenal, dataLen, paquete.getAddress(), paquete.getPort());
                    streamingExecutor.submit(() -> {
                        try { streamEmisor.emitirArchivo(socket, descargaPkt); }
                        catch (Exception e) {
                            LOGGER.warning(() -> "Error en streaming de descarga UDP: " + e.getMessage());
                        }
                    });
                    continue;
                }

                // JSON normal
                byte[] datos = new byte[paquete.getLength()];
                System.arraycopy(buffer, 0, datos, 0, paquete.getLength());
                return new PaqueteDatos(datos,
                        paquete.getAddress().getHostAddress(), paquete.getPort());

            } catch (Exception e) {
                if (socket.isClosed()) {
                    throw new RuntimeException("Socket UDP cerrado", e);
                }
                LOGGER.warning(() -> "Error recibiendo datagrama UDP: " + e.getMessage());
            }
        }
    }

    @Override
    public void detener() {
        if (socket != null && !socket.isClosed()) {
            socket.close();
            LOGGER.info("Transporte UDP detenido");
        }
        streamingExecutor.shutdownNow();
    }

    @Override
    public String getNombre() { return "UDP"; }
}
