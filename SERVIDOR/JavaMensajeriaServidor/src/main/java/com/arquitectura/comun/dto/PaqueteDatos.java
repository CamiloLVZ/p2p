package com.arquitectura.comun.dto;

import java.net.Socket;

public class PaqueteDatos {

    private byte[] data;

    // TCP
    private Socket socket;

    // UDP
    private String hostOrigen;
    private int puertoOrigen;

    // 🔥 Constructor TCP
    public PaqueteDatos(byte[] data, Socket socket) {
        this.data = data;
        this.socket = socket;
    }

    // 🔥 Constructor UDP
    public PaqueteDatos(byte[] data, String hostOrigen, int puertoOrigen) {
        this.data = data;
        this.hostOrigen = hostOrigen;
        this.puertoOrigen = puertoOrigen;
    }

    public byte[] getData() {
        return data;
    }

    public Socket getSocket() {
        return socket;
    }

    public String getHostOrigen() {
        return hostOrigen;
    }

    public int getPuertoOrigen() {
        return puertoOrigen;
    }
}