package com.arquitectura.mensajeria.payload;

public class PayloadEnviarArchivo {

    private String nombre;
    private String contenido; // base64
    private String extension;
    private long tamano;
    private String clientIdDestino;
    private String remitente;

    public PayloadEnviarArchivo() {}

    public PayloadEnviarArchivo(String nombre, String contenido, String extension, long tamano, String clientIdDestino) {
        this.nombre = nombre;
        this.contenido = contenido;
        this.extension = extension;
        this.tamano = tamano;
        this.clientIdDestino = clientIdDestino;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getContenido() {
        return contenido;
    }

    public void setContenido(String contenido) {
        this.contenido = contenido;
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

    public String getClientIdDestino() {
        return clientIdDestino;
    }

    public void setClientIdDestino(String clientIdDestino) {
        this.clientIdDestino = clientIdDestino;
    }

    public String getRemitente() {
        return remitente;
    }

    public void setRemitente(String remitente) {
        this.remitente = remitente;
    }
}