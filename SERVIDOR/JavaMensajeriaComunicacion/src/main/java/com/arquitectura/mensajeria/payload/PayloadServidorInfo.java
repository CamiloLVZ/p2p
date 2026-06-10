package com.arquitectura.mensajeria.payload;

import java.time.LocalDateTime;

public class PayloadServidorInfo {

    private String servidorId;
    private String host;
    private int puerto;
    private String estado; // CONNECTED / DISCONNECTED
    private LocalDateTime ultimaConexion;

    public PayloadServidorInfo() {}

    public String getServidorId() {
        return servidorId;
    }

    public void setServidorId(String servidorId) {
        this.servidorId = servidorId;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPuerto() {
        return puerto;
    }

    public void setPuerto(int puerto) {
        this.puerto = puerto;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public LocalDateTime getUltimaConexion() {
        return ultimaConexion;
    }

    public void setUltimaConexion(LocalDateTime ultimaConexion) {
        this.ultimaConexion = ultimaConexion;
    }
}
