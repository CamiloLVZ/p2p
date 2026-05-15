package com.arquitectura.aplicacion.transferencia;

import com.arquitectura.aplicacion.transferencia.GestorTransferencias.EstadoTransferencia;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.logging.Logger;

/**
 * Receptor de chunks binarios para transferencia de archivos por UDP.
 *
 * Protocolo de datagrama por chunk:
 * <pre>
 * ┌─────────────────────────────────────────────────────────┐
 * │ transferId  │ chunkIndex │ chunkSize  │ chunkData       │
 * │ (36 bytes)  │ (8 bytes)  │ (4 bytes)  │ (chunkSize B)   │
 * └─────────────────────────────────────────────────────────┘
 * </pre>
 *
 * Limitación UDP: el tamaño útil de un datagrama UDP es ~65467 bytes
 * (65535 - 20 IP header - 8 UDP header - 40 overhead mínimo).
 * El chunkSize debe ser <= 60000 bytes para margen seguro.
 *
 * Protocolo de confiabilidad:
 * - Por cada chunk recibido correctamente, el servidor envía ACK(chunkIndex).
 * - Si el servidor detecta un chunk duplicado (chunkIndex ya recibido), re-envía ACK.
 * - El cliente espera ACK antes de enviar el siguiente chunk (stop-and-wait).
 * - Formato ACK: 9 bytes = ACK_BYTE(1) + chunkIndex(8 bytes, long big-endian).
 *
 * Para archivos grandes por UDP esto es lento (un RTT por chunk), pero es correcto.
 * Se recomienda TCP para archivos > 100 MB.
 */
public class StreamReceptorUdp {

    private static final Logger LOGGER = Logger.getLogger(StreamReceptorUdp.class.getName());

    private static final int TRANSFER_ID_LEN = 36;
    private static final int HEADER_LEN      = TRANSFER_ID_LEN + 8 + 4; // 48 bytes
    private static final int MAX_UDP_PAYLOAD = 60_000; // bytes seguros por datagrama
    private static final int MAX_DATAGRAMA   = HEADER_LEN + MAX_UDP_PAYLOAD;

    private static final byte ACK  = 0x01;
    private static final byte NACK = 0x00;

    private final GestorTransferencias gestorTransferencias = GestorTransferencias.getInstance();

    /**
     * Procesa un datagrama UDP que contiene un chunk de archivo.
     * Escribe el chunk en disco y envía ACK o NACK por el socket.
     *
     * Este método es stateless — el estado se mantiene en GestorTransferencias.
     * Debe llamarse desde el hilo que recibe datagramas UDP.
     *
     * @param socket         el DatagramSocket del servidor
     * @param paqueteEntrada el datagrama recibido
     */
    public void procesarChunk(DatagramSocket socket, DatagramPacket paqueteEntrada) {
        byte[] data    = paqueteEntrada.getData();
        int    length  = paqueteEntrada.getLength();
        InetAddress origen = paqueteEntrada.getAddress();
        int puertoOrigen   = paqueteEntrada.getPort();

        if (length < HEADER_LEN) {
            LOGGER.warning("StreamReceptorUdp: datagrama demasiado corto: " + length + " bytes");
            enviarNack(socket, origen, puertoOrigen, -1);
            return;
        }

        // Parsear header
        String transferId = new String(data, 0, TRANSFER_ID_LEN, java.nio.charset.StandardCharsets.UTF_8).trim();
        ByteBuffer buf = ByteBuffer.wrap(data, TRANSFER_ID_LEN, 12);
        long chunkIndex = buf.getLong();
        int  chunkSize  = buf.getInt();

        if (chunkSize <= 0 || chunkSize > MAX_UDP_PAYLOAD || (HEADER_LEN + chunkSize) > length) {
            LOGGER.warning(() -> "StreamReceptorUdp: chunkSize inválido: " + chunkSize + " para transferId: " + transferId);
            enviarNack(socket, origen, puertoOrigen, chunkIndex);
            return;
        }

        EstadoTransferencia estado = gestorTransferencias.obtener(transferId);
        if (estado == null) {
            LOGGER.warning(() -> "StreamReceptorUdp: transferId no encontrado: " + transferId);
            enviarNack(socket, origen, puertoOrigen, chunkIndex);
            return;
        }

        // Extraer datos del chunk
        byte[] chunkData = new byte[chunkSize];
        System.arraycopy(data, HEADER_LEN, chunkData, 0, chunkSize);

        // Serializar escrituras por transferencia — el executor puede despachar chunks en paralelo
        synchronized (estado) {
            // Escribir al archivo temporal (append)
            Path rutaTemporal = estado.getRutaTemporal();
            try (FileChannel fc = FileChannel.open(rutaTemporal,
                    StandardOpenOption.WRITE, StandardOpenOption.APPEND)) {
                java.nio.ByteBuffer dataBuf = java.nio.ByteBuffer.wrap(chunkData);
                while (dataBuf.hasRemaining()) {
                    fc.write(dataBuf);
                }
            } catch (Exception e) {
                LOGGER.severe(() -> "StreamReceptorUdp: error escribiendo chunk " + chunkIndex
                        + " para " + transferId + ": " + e.getMessage());
                enviarNack(socket, origen, puertoOrigen, chunkIndex);
                return;
            }

            // Actualizar digest y contadores
            estado.actualizarDigest(chunkData);
            estado.registrarChunk(chunkSize);
        }

        LOGGER.fine(() -> "Chunk UDP recibido: " + chunkIndex + "/" + (estado.getTotalChunks() - 1)
                + " | transferId: " + transferId);

        // Enviar ACK con el índice del chunk confirmado
        enviarAck(socket, origen, puertoOrigen, chunkIndex);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ACK / NACK
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * ACK: 9 bytes = 0x01 + chunkIndex (long, big-endian)
     */
    private void enviarAck(DatagramSocket socket, InetAddress destino, int puerto, long chunkIndex) {
        byte[] ack = ByteBuffer.allocate(9).put(ACK).putLong(chunkIndex).array();
        enviar(socket, destino, puerto, ack);
    }

    /**
     * NACK: 9 bytes = 0x00 + chunkIndex (long, big-endian)
     */
    private void enviarNack(DatagramSocket socket, InetAddress destino, int puerto, long chunkIndex) {
        byte[] nack = ByteBuffer.allocate(9).put(NACK).putLong(chunkIndex).array();
        enviar(socket, destino, puerto, nack);
    }

    private void enviarRespuesta(DatagramSocket socket, InetAddress destino, int puerto, byte[] data) {
        enviar(socket, destino, puerto, data);
    }

    private void enviar(DatagramSocket socket, InetAddress destino, int puerto, byte[] data) {
        try {
            DatagramPacket paquete = new DatagramPacket(data, data.length, destino, puerto);
            socket.send(paquete);
        } catch (Exception e) {
            LOGGER.warning(() -> "StreamReceptorUdp: error enviando respuesta: " + e.getMessage());
        }
    }
}
