package com.arquitectura.mensajeria.payload;

import java.time.LocalDateTime;

public class PayloadReplicarArchivo {

    private String id;
    private String remitente;
    private String nombreArchivo;
    private String extension;
    private String contenidoCifrado;
    private String hashSha256;
    private long tamano;
    private String servidorOrigen;
    private LocalDateTime fechaRecepcion;
    private String clientIdDestino; // null = broadcast, valor = unicast

    public PayloadReplicarArchivo() {}

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getRemitente() {
        return remitente;
    }

    public void setRemitente(String remitente) {
        this.remitente = remitente;
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

    public String getContenidoCifrado() {
        return contenidoCifrado;
    }

    public void setContenidoCifrado(String contenidoCifrado) {
        this.contenidoCifrado = contenidoCifrado;
    }

    public String getHashSha256() {
        return hashSha256;
    }

    public void setHashSha256(String hashSha256) {
        this.hashSha256 = hashSha256;
    }

    public long getTamano() {
        return tamano;
    }

    public void setTamano(long tamano) {
        this.tamano = tamano;
    }

    public String getServidorOrigen() {
        return servidorOrigen;
    }

    public void setServidorOrigen(String servidorOrigen) {
        this.servidorOrigen = servidorOrigen;
    }

    public LocalDateTime getFechaRecepcion() {
        return fechaRecepcion;
    }

    public void setFechaRecepcion(LocalDateTime fechaRecepcion) {
        this.fechaRecepcion = fechaRecepcion;
    }

    public String getClientIdDestino() {
        return clientIdDestino;
    }

    public void setClientIdDestino(String clientIdDestino) {
        this.clientIdDestino = clientIdDestino;
    }
}
