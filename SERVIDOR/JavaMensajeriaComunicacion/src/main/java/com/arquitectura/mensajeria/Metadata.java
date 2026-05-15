package com.arquitectura.mensajeria;

import com.arquitectura.mensajeria.enums.Protocolo;

import java.time.LocalDateTime;

public class Metadata {

    private String idMensaje;
    private LocalDateTime timestamp;
    private String clientId;
    private Protocolo protocolo;

    public Metadata() {}

    public Metadata(String idMensaje, LocalDateTime timestamp, String clientId, Protocolo protocolo) {
        this.idMensaje = idMensaje;
        this.timestamp = timestamp;
        this.clientId = clientId;
        this.protocolo = protocolo;
    }

    public String getIdMensaje() {
        return idMensaje;
    }

    public void setIdMensaje(String idMensaje) {
        this.idMensaje = idMensaje;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public Protocolo getProtocolo() {
        return protocolo;
    }

    public void setProtocolo(Protocolo protocolo) {
        this.protocolo = protocolo;
    }
}