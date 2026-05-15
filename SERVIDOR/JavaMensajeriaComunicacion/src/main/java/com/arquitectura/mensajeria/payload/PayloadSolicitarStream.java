package com.arquitectura.mensajeria.payload;

/**
 * Payload del mensaje SOLICITAR_STREAM.
 *
 * El cliente lo envía para pedir la descarga de un archivo.
 * El servidor responde con INICIAR_DESCARGA (metadatos) y luego
 * abre una conexión de streaming para enviar los chunks.
 */
public class PayloadSolicitarStream {

    /** ID del archivo en base de datos. */
    private String archivoId;

    public PayloadSolicitarStream() {}

    public PayloadSolicitarStream(String archivoId) {
        this.archivoId = archivoId;
    }

    public String getArchivoId() { return archivoId; }
    public void setArchivoId(String archivoId) { this.archivoId = archivoId; }
}
