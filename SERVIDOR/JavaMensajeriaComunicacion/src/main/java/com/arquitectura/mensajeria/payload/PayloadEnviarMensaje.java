package com.arquitectura.mensajeria.payload;

public class PayloadEnviarMensaje {
    private String autor;
    private String contenido;
    private String destinatario; // null = broadcast

    public PayloadEnviarMensaje() {}

    public PayloadEnviarMensaje(String autor, String contenido) {
        this.autor = autor;
        this.contenido = contenido;
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

    public String getDestinatario() {
        return destinatario;
    }

    public void setDestinatario(String destinatario) {
        this.destinatario = destinatario;
    }

} 
