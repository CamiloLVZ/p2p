package com.arquitectura.aplicacion.transferencia;

import com.arquitectura.aplicacion.transferencia.GestorDescargasActivas.DescargaAutorizada;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.logging.Logger;

/**
 * Emisor de chunks binarios para descarga de archivos por UDP.
 *
 * Los ACKs del cliente NO se reciben aquí con socket.receive() — eso causaría
 * competencia con el loop principal de UdpProtocoloTransporte por el mismo socket.
 * En cambio, el loop deposita los ACKs en GestorDescargasActivas.colaAcks
 * y este emisor los consume con recibirAck().
 */
public class StreamEmisorUdp {

    private static final Logger LOGGER = Logger.getLogger(StreamEmisorUdp.class.getName());

    private static final int  TRANSFER_ID_LEN = 36;
    private static final byte STREAM_DOWN      = 0x03;
    private static final byte ACK              = 0x01;
    private static final int  ACK_TIMEOUT_MS   = 5_000;
    private static final int  MAX_RETRIES      = 5;

    private final GestorDescargasActivas gestorDescargas = GestorDescargasActivas.getInstance();

    public void emitirArchivo(DatagramSocket socket, DatagramPacket paqueteInicial) {
        byte[] data   = paqueteInicial.getData();
        int    length = paqueteInicial.getLength();
        InetAddress clienteAddr = paqueteInicial.getAddress();
        int clientePuerto       = paqueteInicial.getPort();

        if (length < TRANSFER_ID_LEN) {
            LOGGER.warning("StreamEmisorUdp: datagrama demasiado corto para contener transferId");
            return;
        }

        String transferId = new String(data, 0, TRANSFER_ID_LEN,
                java.nio.charset.StandardCharsets.UTF_8).trim();

        DescargaAutorizada descarga = gestorDescargas.obtener(transferId);
        if (descarga == null) {
            LOGGER.warning(() -> "StreamEmisorUdp: transferId no encontrado: " + transferId);
            return;
        }

        Path rutaArchivo = descarga.getRutaArchivo();
        int chunkSize    = descarga.getChunkSize();

        LOGGER.info(() -> "StreamEmisorUdp: iniciando envío de " + rutaArchivo
                + " | chunk=" + chunkSize + " | transferId=" + transferId);

        // Vaciar ACKs residuales de transferencias anteriores
        gestorDescargas.limpiarAcks();

        try (FileChannel fc = FileChannel.open(rutaArchivo, StandardOpenOption.READ)) {
            byte[] buffer = new byte[chunkSize];
            long chunkIndex = 0;
            int leido;

            while ((leido = leerDelCanal(fc, buffer, chunkSize)) > 0) {
                final int bytesChunk = leido;
                boolean confirmado = false;
                int intentos = 0;

                while (!confirmado && intentos < MAX_RETRIES) {
                    // Frame: señal(1) + id(36) + index(8) + size(4) + datos
                    ByteBuffer frame = ByteBuffer.allocate(1 + 36 + 8 + 4 + bytesChunk);
                    frame.put(STREAM_DOWN);
                    frame.put(padTransferId(transferId));
                    frame.putLong(chunkIndex);
                    frame.putInt(bytesChunk);
                    frame.put(buffer, 0, bytesChunk);

                    socket.send(new DatagramPacket(
                            frame.array(), frame.array().length, clienteAddr, clientePuerto));

                    // Esperar ACK desde la cola (el loop lo deposita ahí)
                    byte[] ackData = gestorDescargas.recibirAck(ACK_TIMEOUT_MS);
                    if (ackData != null && ackData.length >= 9) {
                        ByteBuffer ackBB = ByteBuffer.wrap(ackData);
                        byte tipo   = ackBB.get();
                        long ackIdx = ackBB.getLong();
                        if (tipo == ACK && ackIdx == chunkIndex) {
                            confirmado = true;
                        } else {
                            intentos++;
                        }
                    } else {
                        // timeout
                        intentos++;
                    }
                }

                if (!confirmado) {
                    final long failedChunk = chunkIndex;
                    LOGGER.warning(() -> "StreamEmisorUdp: sin ACK para chunk " + failedChunk
                            + " tras " + MAX_RETRIES + " intentos. Abortando.");
                    break;
                }

                final long ci = chunkIndex;
                LOGGER.fine(() -> "Chunk UDP enviado: " + ci);
                chunkIndex++;
            }
        } catch (Exception e) {
            LOGGER.severe(() -> "StreamEmisorUdp: error emitiendo archivo: " + e.getMessage());
        }

        gestorDescargas.eliminar(transferId);
        gestorDescargas.limpiarAcks();
        LOGGER.info(() -> "StreamEmisorUdp: emisión completa para " + transferId);
    }

    private int leerDelCanal(FileChannel fc, byte[] buffer, int maxBytes) throws Exception {
        java.nio.ByteBuffer bb = java.nio.ByteBuffer.wrap(buffer, 0, maxBytes);
        int total = 0;
        while (bb.hasRemaining()) {
            int n = fc.read(bb);
            if (n == -1) break;
            total += n;
        }
        return total == 0 ? -1 : total;
    }

    private byte[] padTransferId(String transferId) {
        byte[] raw = transferId.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (raw.length == TRANSFER_ID_LEN) return raw;
        byte[] padded = new byte[TRANSFER_ID_LEN];
        System.arraycopy(raw, 0, padded, 0, Math.min(raw.length, TRANSFER_ID_LEN));
        return padded;
    }
}
