package com.arquitectura.mensajeria.payload;

import java.time.LocalDateTime;

public class PayloadEntregarMensaje {

    private String destinatario;
    private String autor;
    private String contenido;
    private String servidorOrigen;
    private LocalDateTime timestamp;

    public PayloadEntregarMensaje() {}

    public String getDestinatario() {
        return destinatario;
    }

    public void setDestinatario(String destinatario) {
        this.destinatario = destinatario;
    }

    public String getAutor() {
        return autor;
    }

    public void setAutor(String autor) {
        this.autor = autor;
    }

    public String getContenido() {
        return contenido;
    }

    public void setContenido(String contenido) {
        this.contenido = contenido;
    }

    public String getServidorOrigen() {
        return servidorOrigen;
    }

    public void setServidorOrigen(String servidorOrigen) {
        this.servidorOrigen = servidorOrigen;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
}
