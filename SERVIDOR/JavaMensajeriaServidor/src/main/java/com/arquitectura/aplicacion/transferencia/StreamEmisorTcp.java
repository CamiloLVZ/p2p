package com.arquitectura.aplicacion.transferencia;

import com.arquitectura.aplicacion.transferencia.GestorDescargasActivas.DescargaAutorizada;

import java.io.*;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.logging.Logger;

/**
 * Emisor de chunks binarios para descarga de archivos por TCP.
 *
 * Protocolo de frame por chunk (mismo formato que StreamReceptorTcp para consistencia):
 * <pre>
 * ┌─────────────────────────────────────────────────────────┐
 * │ transferId  │ chunkIndex │ chunkSize  │ chunkData       │
 * │ (36 bytes)  │ (8 bytes)  │ (4 bytes)  │ (chunkSize B)   │
 * └─────────────────────────────────────────────────────────┘
 * </pre>
 *
 * El servidor lee el transferId del socket (enviado por el cliente como señal inicial),
 * busca el archivo en GestorDescargasActivas, y envía los chunks.
 * El cliente responde con ACK (0x01) antes de recibir el siguiente chunk.
 *
 * Protocolo de señal de descarga:
 * 1. Cliente envía: 0x03 (señal) + transferId (36 bytes UTF-8).
 * 2. Servidor responde con chunks usando el protocolo de frame de arriba.
 * 3. Cliente envía ACK (0x01) por cada chunk recibido.
 */
public class StreamEmisorTcp {

    private static final Logger LOGGER = Logger.getLogger(StreamEmisorTcp.class.getName());

    private static final int TRANSFER_ID_LEN = 36;
    private static final byte ACK  = 0x01;
    private static final byte NACK = 0x00;

    private final GestorDescargasActivas gestorDescargas = GestorDescargasActivas.getInstance();

    /**
     * Lee el transferId del socket, busca el archivo y envía todos los chunks.
     *
     * @param socket conexión TCP activa con el cliente (ya se consumió el byte 0x03)
     */
    public void emitirArchivo(Socket socket) throws IOException {
        InputStream is  = socket.getInputStream();
        OutputStream os = socket.getOutputStream();

        // Leer el transferId (36 bytes fijos)
        byte[] idBytes = leerExacto(is, TRANSFER_ID_LEN);
        if (idBytes == null) {
            LOGGER.warning("StreamEmisorTcp: EOF al leer transferId");
            return;
        }
        String transferId = new String(idBytes, java.nio.charset.StandardCharsets.UTF_8).trim();

        DescargaAutorizada descarga = gestorDescargas.obtener(transferId);
        if (descarga == null) {
            LOGGER.warning(() -> "StreamEmisorTcp: transferId no encontrado: " + transferId);
            os.write(NACK);
            os.flush();
            return;
        }

        Path rutaArchivo = descarga.getRutaArchivo();
        int chunkSize    = descarga.getChunkSize();

        LOGGER.info(() -> "StreamEmisorTcp: iniciando envío de " + rutaArchivo
                + " | chunk=" + chunkSize + " bytes | transferId=" + transferId);

        try (FileChannel fc = FileChannel.open(rutaArchivo, StandardOpenOption.READ)) {
            byte[] buffer = new byte[chunkSize];
            long chunkIndex = 0;
            int leido;

            ByteArrayOutputStream baos = new ByteArrayOutputStream(chunkSize + 48);

            while ((leido = leerDelCanal(fc, buffer, chunkSize)) > 0) {
                // Construir frame: transferId(36) + chunkIndex(8) + chunkSize(4) + datos
                baos.reset();
                baos.write(padTransferId(transferId));
                baos.write(ByteBuffer.allocate(8).putLong(chunkIndex).array());
                baos.write(ByteBuffer.allocate(4).putInt(leido).array());
                baos.write(buffer, 0, leido);

                os.write(baos.toByteArray());
                os.flush();

                // Esperar ACK del cliente
                int ack = is.read();
                final long ci = chunkIndex;
                final int bytesLeidos = leido;
                if (ack != ACK) {
                    LOGGER.warning(() -> "StreamEmisorTcp: NACK o EOF del cliente en chunk "
                            + ci + " | transferId=" + transferId);
                    break;
                }

                LOGGER.fine(() -> "Chunk enviado: " + ci + " | bytes=" + bytesLeidos);
                chunkIndex++;
            }
        }

        gestorDescargas.eliminar(transferId);
        LOGGER.info(() -> "StreamEmisorTcp: emisión completa para " + transferId);
    }

    /**
     * Lee hasta {@code maxBytes} del FileChannel hacia el buffer.
     * Devuelve la cantidad de bytes leídos, o -1 si llegó al EOF.
     */
    private int leerDelCanal(FileChannel fc, byte[] buffer, int maxBytes) throws IOException {
        ByteBuffer bb = ByteBuffer.wrap(buffer, 0, maxBytes);
        int total = 0;
        while (bb.hasRemaining()) {
            int n = fc.read(bb);
            if (n == -1) break;
            total += n;
        }
        return total == 0 ? -1 : total;
    }

    private byte[] leerExacto(InputStream is, int length) throws IOException {
        byte[] buffer = new byte[length];
        int offset = 0;
        while (offset < length) {
            int n = is.read(buffer, offset, length - offset);
            if (n == -1) return null;
            offset += n;
        }
        return buffer;
    }

    private byte[] padTransferId(String transferId) {
        byte[] raw = transferId.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (raw.length == TRANSFER_ID_LEN) return raw;
        byte[] padded = new byte[TRANSFER_ID_LEN];
        System.arraycopy(raw, 0, padded, 0, Math.min(raw.length, TRANSFER_ID_LEN));
        return padded;
    }
}
