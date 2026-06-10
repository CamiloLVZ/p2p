package com.arquitectura.mensajeria.payload;

import java.time.LocalDateTime;
import java.util.List;

public class PayloadEstadoServidor {

    private String servidorId;
    private String host;
    private int puerto;
    private long uptimeSeconds;
    private int clientesConectados;
    private int peersConectados;
    private List<PayloadServidorInfo> peers;
    private LocalDateTime timestamp;

    public PayloadEstadoServidor() {}

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

    public long getUptimeSeconds() {
        return uptimeSeconds;
    }

    public void setUptimeSeconds(long uptimeSeconds) {
        this.uptimeSeconds = uptimeSeconds;
    }

    public int getClientesConectados() {
        return clientesConectados;
    }

    public void setClientesConectados(int clientesConectados) {
        this.clientesConectados = clientesConectados;
    }

    public int getPeersConectados() {
        return peersConectados;
    }

    public void setPeersConectados(int peersConectados) {
        this.peersConectados = peersConectados;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public List<PayloadServidorInfo> getPeers() {
        return peers;
    }

    public void setPeers(List<PayloadServidorInfo> peers) {
        this.peers = peers;
    }
}
