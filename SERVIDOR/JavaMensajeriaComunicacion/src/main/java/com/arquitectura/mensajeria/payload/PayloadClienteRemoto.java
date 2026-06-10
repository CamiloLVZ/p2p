package com.arquitectura.mensajeria.payload;

public class PayloadClienteRemoto {

    private String username;
    private String ip;
    private int puerto;
    private String protocolo;
    private String servidorOrigen;

    public PayloadClienteRemoto() {}

    public PayloadClienteRemoto(String username, String ip, int puerto, String protocolo, String servidorOrigen) {
        this.username = username;
        this.ip = ip;
        this.puerto = puerto;
        this.protocolo = protocolo;
        this.servidorOrigen = servidorOrigen;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public int getPuerto() {
        return puerto;
    }

    public void setPuerto(int puerto) {
        this.puerto = puerto;
    }

    public String getProtocolo() {
        return protocolo;
    }

    public void setProtocolo(String protocolo) {
        this.protocolo = protocolo;
    }

    public String getServidorOrigen() {
        return servidorOrigen;
    }

    public void setServidorOrigen(String servidorOrigen) {
        this.servidorOrigen = servidorOrigen;
    }
}
