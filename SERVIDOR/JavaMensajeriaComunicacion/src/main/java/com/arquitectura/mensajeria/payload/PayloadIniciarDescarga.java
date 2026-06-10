package com.arquitectura.mensajeria.payload;

/**
 * Payload de la respuesta INICIAR_DESCARGA.
 *
 * El servidor lo envía en respuesta a SOLICITAR_STREAM.
 * Contiene todos los metadatos que el cliente necesita para:
 * - abrir el archivo destino con el nombre correcto
 * - mostrar progreso real en la UI
 * - validar integridad al terminar (hash SHA-256)
 */
public class PayloadIniciarDescarga {

    /** Identificador único de la sesión de descarga (UUID generado por el servidor). */
    private String transferId;

    /** Nombre original del archivo. */
    private String nombreArchivo;

    /** Extensión sin punto. */
    private String extension;

    /** Tamaño total del archivo en bytes. */
    private long tamanoTotal;

    /** Cantidad de chunks que el servidor va a enviar. */
    private long totalChunks;

    /** Tamaño de cada chunk (el último puede ser menor). */
    private int tamanoChunk;

    /** Hash SHA-256 del archivo en Base64 — para que el cliente valide al terminar. */
    private String hashSha256;

    public PayloadIniciarDescarga() {}

    public PayloadIniciarDescarga(String transferId, String nombreArchivo, String extension,
                                  long tamanoTotal, long totalChunks, int tamanoChunk,
                                  String hashSha256) {
        this.transferId = transferId;
        this.nombreArchivo = nombreArchivo;
        this.extension = extension;
        this.tamanoTotal = tamanoTotal;
        this.totalChunks = totalChunks;
        this.tamanoChunk = tamanoChunk;
        this.hashSha256 = hashSha256;
    }

    public String getTransferId()      { return transferId; }
    public void setTransferId(String v){ this.transferId = v; }

    public String getNombreArchivo()        { return nombreArchivo; }
    public void setNombreArchivo(String v)  { this.nombreArchivo = v; }

    public String getExtension()        { return extension; }
    public void setExtension(String v)  { this.extension = v; }

    public long getTamanoTotal()        { return tamanoTotal; }
    public void setTamanoTotal(long v)  { this.tamanoTotal = v; }

    public long getTotalChunks()        { return totalChunks; }
    public void setTotalChunks(long v)  { this.totalChunks = v; }

    public int getTamanoChunk()         { return tamanoChunk; }
    public void setTamanoChunk(int v)   { this.tamanoChunk = v; }

    public String getHashSha256()       { return hashSha256; }
    public void setHashSha256(String v) { this.hashSha256 = v; }
}
