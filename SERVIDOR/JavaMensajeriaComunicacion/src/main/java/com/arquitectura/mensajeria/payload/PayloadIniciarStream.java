package com.arquitectura.mensajeria.payload;

/**
 * Payload del mensaje de control INICIAR_STREAM.
 *
 * El cliente lo envía antes de empezar a mandar chunks binarios.
 * El servidor responde con el mismo transferId para confirmar que
 * está listo para recibir.
 */
public class PayloadIniciarStream {

    /** Identificador único de la transferencia (UUID generado por el cliente). */
    private String transferId;

    /** Nombre original del archivo (sin modificar, sin sufijos .partN). */
    private String nombreArchivo;

    /** Extensión del archivo sin el punto, ej. "pdf", "mp4". */
    private String extension;

    /** Tamaño total del archivo en bytes. */
    private long tamanoTotal;

    /** Cantidad total de chunks que se van a enviar. */
    private long totalChunks;

    /** Tamaño de cada chunk en bytes (el último puede ser menor). */
    private int tamanoChunk;

    /**
     * ID del cliente destino para envío unicast.
     * Si es null, el servidor hace broadcast a todos los clientes.
     */
    private String clientIdDestino;

    public PayloadIniciarStream() {}

    public PayloadIniciarStream(String transferId, String nombreArchivo, String extension,
                                long tamanoTotal, long totalChunks, int tamanoChunk) {
        this.transferId = transferId;
        this.nombreArchivo = nombreArchivo;
        this.extension = extension;
        this.tamanoTotal = tamanoTotal;
        this.totalChunks = totalChunks;
        this.tamanoChunk = tamanoChunk;
    }

    public String getTransferId() { return transferId; }
    public void setTransferId(String transferId) { this.transferId = transferId; }

    public String getNombreArchivo() { return nombreArchivo; }
    public void setNombreArchivo(String nombreArchivo) { this.nombreArchivo = nombreArchivo; }

    public String getExtension() { return extension; }
    public void setExtension(String extension) { this.extension = extension; }

    public long getTamanoTotal() { return tamanoTotal; }
    public void setTamanoTotal(long tamanoTotal) { this.tamanoTotal = tamanoTotal; }

    public long getTotalChunks() { return totalChunks; }
    public void setTotalChunks(long totalChunks) { this.totalChunks = totalChunks; }

    public int getTamanoChunk() { return tamanoChunk; }
    public void setTamanoChunk(int tamanoChunk) { this.tamanoChunk = tamanoChunk; }

    public String getClientIdDestino() { return clientIdDestino; }
    public void setClientIdDestino(String clientIdDestino) { this.clientIdDestino = clientIdDestino; }
}
