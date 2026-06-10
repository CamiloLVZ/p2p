package com.cliente.domain.model;

public class Client {
    private String id;
    private String name;
    private String ip;
    private int port;
    private String status;
    private String servidorOrigen;

    public Client() {}

    public Client(String id, String name, String ip, int port, String status) {
        this.id = id;
        this.name = name;
        this.ip = ip;
        this.port = port;
        this.status = status;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getIp() { return ip; }
    public void setIp(String ip) { this.ip = ip; }
    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getServidorOrigen() { return servidorOrigen; }
    public void setServidorOrigen(String servidorOrigen) { this.servidorOrigen = servidorOrigen; }
}
