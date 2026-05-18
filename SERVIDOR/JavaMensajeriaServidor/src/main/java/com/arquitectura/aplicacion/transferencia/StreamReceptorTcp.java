package com.arquitectura.aplicacion.transferencia;

import com.arquitectura.aplicacion.transferencia.GestorTransferencias.EstadoTransferencia;

import java.io.*;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;
import java.util.logging.Logger;

/**
 * Receptor de chunks binarios para transferencia de archivos por TCP.
 *
 * Protocolo de frame binario por chunk:
 * <pre>
 * ┌─────────────────────────────────────────────────────────┐
 * │ transferId  │ chunkIndex │ chunkSize  │ chunkData       │
 * │ (36 bytes)  │ (8 bytes)  │ (4 bytes)  │ (chunkSize B)   │
 * └─────────────────────────────────────────────────────────┘
 * </pre>
 *
 * El receptor:
 * 1. Lee el header (48 bytes fijos).
 * 2. Lee exactamente chunkSize bytes de datos.
 * 3. Escribe los datos en el archivo temporal usando FileChannel (sin copiar a RAM).
 * 4. Actualiza el digest SHA-256 incremental.
 * 5. Responde con un ACK de 1 byte (0x01) para que el cliente sepa que puede enviar el siguiente.
 * 6. Repite hasta recibir todos los chunks declarados en INICIAR_STREAM.
 *
 * En caso de error envía un NACK (0x00) y cierra la conexión.
 */
public class StreamReceptorTcp {

    private static final Logger LOGGER = Logger.getLogger(StreamReceptorTcp.class.getName());

    /** Longitud fija del transferId en bytes (UUID sin guiones + guiones = 36 chars UTF-8). */
    private static final int TRANSFER_ID_LEN = 36;

    /** Tamaño del header: transferId(36) + chunkIndex(8) + chunkSize(4) = 48 bytes. */
    private static final int HEADER_LEN = TRANSFER_ID_LEN + 8 + 4;

    private static final byte ACK  = 0x01;
    private static final byte NACK = 0x00;

    private final GestorTransferencias gestorTransferencias = GestorTransferencias.getInstance();

    /**
     * Máximo de reintentos para encontrar la transferencia en el gestor.
     * Necesario porque el cliente puede abrir la conexión de streaming
     * antes de que el handler de INICIAR_STREAM termine de registrarla
     * (race condition entre el worker JSON y el hilo de streaming).
     */
    private static final int MAX_RETRIES_TRANSFER_ID   = 20;
    private static final long RETRY_DELAY_MS            = 100;

    /**
     * Recibe todos los chunks del archivo a través del socket TCP dado.
     * Bloquea hasta recibir todos los chunks o hasta error.
     *
     * @param socket socket con la conexión TCP activa del cliente
     * @throws IOException si ocurre un error de red o disco
     */
    public void recibirArchivo(Socket socket) throws IOException {
        InputStream is = socket.getInputStream();
        OutputStream os = socket.getOutputStream();

        LOGGER.info("StreamReceptorTcp: iniciando recepción de chunks");

        EstadoTransferencia ultimoEstadoS2S = null;

        // Abrir el FileChannel UNA sola vez para toda la transferencia.
        // Antes se abría y cerraba por cada chunk — 1500 syscalls open/close para 1 GB.
        // Ahora se mantiene abierto en append hasta que terminen todos los chunks.
        EstadoTransferencia estadoInicial = null;
        FileChannel fc = null;

        try {
            while (true) {
                byte[] header = leerExacto(is, HEADER_LEN);
                if (header == null) {
                    LOGGER.info("StreamReceptorTcp: conexión cerrada por el cliente (EOF)");
                    if (ultimoEstadoS2S != null && gestorTransferencias.existe(ultimoEstadoS2S.getTransferId())) {
                        gestorTransferencias.finalizarTransferenciaS2S(ultimoEstadoS2S.getTransferId());
                    }
                    break;
                }

                String transferId = new String(header, 0, TRANSFER_ID_LEN, java.nio.charset.StandardCharsets.UTF_8);
                ByteBuffer buf = ByteBuffer.wrap(header, TRANSFER_ID_LEN, 12);
                long chunkIndex = buf.getLong();
                int chunkSize   = buf.getInt();

                if (chunkSize <= 0 || chunkSize > 8 * 1024 * 1024) {
                    LOGGER.warning(() -> "StreamReceptorTcp: chunkSize inválido: " + chunkSize);
                    os.write(NACK);
                    os.flush();
                    break;
                }

                EstadoTransferencia estado = gestorTransferencias.obtener(transferId.trim());
                if (estado == null) {
                    LOGGER.warning(() -> "StreamReceptorTcp: transferId no encontrado: " + transferId);
                    os.write(NACK);
                    os.flush();
                    break;
                }

                if (estado.isEsReplicacionS2S()) {
                    ultimoEstadoS2S = estado;
                }

                // Abrir el FileChannel la primera vez (o si cambia la transferencia)
                if (fc == null || estadoInicial != estado) {
                    if (fc != null) {
                        fc.close();
                    }
                    fc = FileChannel.open(estado.getRutaTemporal(),
                            StandardOpenOption.WRITE, StandardOpenOption.APPEND);
                    estadoInicial = estado;
                }

                byte[] datos = leerExacto(is, chunkSize);
                if (datos == null) {
                    LOGGER.warning(() -> "StreamReceptorTcp: EOF inesperado leyendo datos del chunk " + chunkIndex);
                    os.write(NACK);
                    os.flush();
                    break;
                }

                // Escribir al FileChannel ya abierto — sin open/close por chunk
                ByteBuffer dataBuf = ByteBuffer.wrap(datos);
                while (dataBuf.hasRemaining()) {
                    fc.write(dataBuf);
                }

                estado.actualizarDigest(datos);
                estado.registrarChunk(chunkSize);

                LOGGER.fine(() -> "Chunk recibido: " + chunkIndex + "/" + (estado.getTotalChunks() - 1)
                        + " | transferId: " + transferId.trim()
                        + " | bytes: " + chunkSize);

                os.write(ACK);
                os.flush();

                if (estado.estaCompleta()) {
                    LOGGER.info(() -> "StreamReceptorTcp: todos los chunks recibidos para " + transferId.trim());
                    if (estado.isEsReplicacionS2S()) {
                        gestorTransferencias.finalizarTransferenciaS2S(transferId.trim());
                    }
                    break;
                }
            }
        } finally {
            // Garantizar que el FileChannel se cierre siempre, incluso en error
            if (fc != null) {
                try { fc.close(); } catch (Exception ignored) {}
            }
        }
    }

    /**
     * Procesa un chunk: lee los datos, los escribe al FileChannel, actualiza digest,
     * envía ACK y retorna el estado actualizado. Retorna null si hubo error.
     */
    private EstadoTransferencia procesarChunk(InputStream is, OutputStream os,
                                               FileChannel fc,
                                               EstadoTransferencia estado,
                                               String transferId,
                                               long chunkIndex, int chunkSize) throws IOException {
        if (chunkSize <= 0 || chunkSize > 8 * 1024 * 1024) {
            LOGGER.warning(() -> "StreamReceptorTcp: chunkSize inválido: " + chunkSize);
            os.write(NACK); os.flush();
            return null;
        }

        byte[] datos = leerExacto(is, chunkSize);
        if (datos == null) {
            LOGGER.warning(() -> "StreamReceptorTcp: EOF inesperado leyendo datos del chunk " + chunkIndex);
            os.write(NACK); os.flush();
            return null;
        }

        ByteBuffer dataBuf = ByteBuffer.wrap(datos);
        while (dataBuf.hasRemaining()) fc.write(dataBuf);

        estado.actualizarDigest(datos);
        estado.registrarChunk(chunkSize);

        LOGGER.fine(() -> "Chunk recibido: " + chunkIndex + "/" + (estado.getTotalChunks() - 1)
                + " | transferId: " + transferId + " | bytes: " + chunkSize);

        os.write(ACK);
        os.flush();
        return estado;
    }

    private void finalizarSiS2S(EstadoTransferencia estado) {
        if (estado.isEsReplicacionS2S()) {
            gestorTransferencias.finalizarTransferenciaS2S(estado.getTransferId());
        }
    }

    /**
     * Lee exactamente {@code length} bytes del InputStream.
     * Devuelve null si el stream llega a EOF antes de completar la lectura.
     */
    private byte[] leerExacto(InputStream is, int length) throws IOException {
        byte[] buffer = new byte[length];
        int offset = 0;
        while (offset < length) {
            int read = is.read(buffer, offset, length - offset);
            if (read == -1) {
                return null; // EOF
            }
            offset += read;
        }
        return buffer;
    }
}
