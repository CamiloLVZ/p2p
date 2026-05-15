package com.arquitectura.mensajeria.payload;

public class PayloadReplicarArchivoStream {

    private String id;
    private String nombreArchivo;
    private String extension;
    private long tamano;
    private String hashSha256;
    private String servidorOrigen;
    private String remitente;

    public PayloadReplicarArchivoStream() {}

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getNombreArchivo() {
        return nombreArchivo;
    }

    public void setNombreArchivo(String nombreArchivo) {
        this.nombreArchivo = nombreArchivo;
    }

    public String getExtension() {
        return extension;
    }

    public void setExtension(String extension) {
        this.extension = extension;
    }

    public long getTamano() {
        return tamano;
    }

    public void setTamano(long tamano) {
        this.tamano = tamano;
    }

    public String getHashSha256() {
        return hashSha256;
    }

    public void setHashSha256(String hashSha256) {
        this.hashSha256 = hashSha256;
    }

    public String getServidorOrigen() {
        return servidorOrigen;
    }

    public void setServidorOrigen(String servidorOrigen) {
        this.servidorOrigen = servidorOrigen;
    }

    public String getRemitente() {
        return remitente;
    }

    public void setRemitente(String remitente) {
        this.remitente = remitente;
    }
}
