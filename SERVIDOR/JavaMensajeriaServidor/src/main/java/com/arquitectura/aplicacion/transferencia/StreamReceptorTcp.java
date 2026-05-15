package com.arquitectura.aplicacion.transferencia;

import com.arquitectura.aplicacion.transferencia.GestorTransferencias.EstadoTransferencia;

import java.io.*;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.logging.Level;
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

        // Guarda el último estado S2S visto para poder finalizarlo en EOF
        EstadoTransferencia ultimoEstadoS2S = null;

        while (true) {
            // 1. Leer header completo
            byte[] header = leerExacto(is, HEADER_LEN);
            if (header == null) {
                // EOF — el cliente cerró la conexión normalmente
                LOGGER.info("StreamReceptorTcp: conexión cerrada por el cliente (EOF)");
                // Si era S2S y aún no se finalizó (estaCompleta() pudo no haberse alcanzado
                // porque totalChunks era una estimación), finalizamos aquí.
                if (ultimoEstadoS2S != null && gestorTransferencias.existe(ultimoEstadoS2S.getTransferId())) {
                    gestorTransferencias.finalizarTransferenciaS2S(ultimoEstadoS2S.getTransferId());
                }
                break;
            }

            // 2. Parsear header
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

            // 3. Buscar transferencia activa
            EstadoTransferencia estado = gestorTransferencias.obtener(transferId.trim());
            if (estado == null) {
                LOGGER.warning(() -> "StreamReceptorTcp: transferId no encontrado: " + transferId);
                os.write(NACK);
                os.flush();
                break;
            }

            // Trackear el último estado S2S visto (para el caso de EOF)
            if (estado.isEsReplicacionS2S()) {
                ultimoEstadoS2S = estado;
            }

            // 4. Leer datos del chunk
            byte[] datos = leerExacto(is, chunkSize);
            if (datos == null) {
                LOGGER.warning(() -> "StreamReceptorTcp: EOF inesperado leyendo datos del chunk " + chunkIndex);
                os.write(NACK);
                os.flush();
                break;
            }

            // 5. Escribir chunk al archivo temporal usando FileChannel (append)
            Path rutaTemporal = estado.getRutaTemporal();
            try (FileChannel fc = FileChannel.open(rutaTemporal,
                    StandardOpenOption.WRITE, StandardOpenOption.APPEND)) {
                ByteBuffer dataBuf = ByteBuffer.wrap(datos);
                while (dataBuf.hasRemaining()) {
                    fc.write(dataBuf);
                }
            }

            // 6. Actualizar digest y contadores
            estado.actualizarDigest(datos);
            estado.registrarChunk(chunkSize);

            LOGGER.fine(() -> "Chunk recibido: " + chunkIndex + "/" + (estado.getTotalChunks() - 1)
                    + " | transferId: " + transferId.trim()
                    + " | bytes: " + chunkSize);

            // 7. Enviar ACK
            os.write(ACK);
            os.flush();

            // 8. Si ya llegaron todos los chunks, salir del bucle
            if (estado.estaCompleta()) {
                LOGGER.info(() -> "StreamReceptorTcp: todos los chunks recibidos para " + transferId.trim());
                // Finalizar transferencia S2S si corresponde
                if (estado.isEsReplicacionS2S()) {
                    gestorTransferencias.finalizarTransferenciaS2S(transferId.trim());
                }
                break;
            }
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
