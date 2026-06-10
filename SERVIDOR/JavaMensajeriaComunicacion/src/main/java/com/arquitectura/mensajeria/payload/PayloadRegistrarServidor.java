package com.arquitectura.mensajeria.payload;

import java.util.List;

public class PayloadRegistrarServidor {

    private String servidorId;
    private String host;
    private int puerto;
    private String version;
    private List<PayloadServidorInfo> peersConocidos;

    public PayloadRegistrarServidor() {}

    public PayloadRegistrarServidor(String servidorId, String host, int puerto, String version) {
        this.servidorId = servidorId;
        this.host = host;
        this.puerto = puerto;
        this.version = version;
    }

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

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public List<PayloadServidorInfo> getPeersConocidos() {
        return peersConocidos;
    }

    public void setPeersConocidos(List<PayloadServidorInfo> peersConocidos) {
        this.peersConocidos = peersConocidos;
    }
}
