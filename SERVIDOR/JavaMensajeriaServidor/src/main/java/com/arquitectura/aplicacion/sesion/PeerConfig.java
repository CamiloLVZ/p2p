package com.arquitectura.aplicacion.sesion;

/**
 * Configuracion inmutable de un servidor peer leida desde application.properties.
 */
public final class PeerConfig {

    private final String servidorId;
    private final String host;
    private final int puerto;

    public PeerConfig(String servidorId, String host, int puerto) {
        this.servidorId = servidorId;
        this.host = host;
        this.puerto = puerto;
    }

    public String getServidorId() {
        return servidorId;
    }

    public String getHost() {
        return host;
    }

    public int getPuerto() {
        return puerto;
    }

    @Override
    public String toString() {
        return "PeerConfig{servidorId='" + servidorId + "', host='" + host + "', puerto=" + puerto + "}";
    }
}
