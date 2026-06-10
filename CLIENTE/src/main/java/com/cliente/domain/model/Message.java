package com.cliente.domain.model;

public class Message {
    private String clientId;
    private String content;
    private String timestamp;
    private String hashSha256;
    private String contenidoCifrado;
    private String ipRemitente;
    private String origen;
    private String destinatario;   // null = broadcast
    private String servidorOrigen; // ID of the server that stored the message

    public Message() {}

    public Message(String clientId, String content, String timestamp) {
        this.clientId = clientId;
        this.content = content;
        this.timestamp = timestamp;
    }

    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
    public String getHashSha256() { return hashSha256; }
    public void setHashSha256(String hashSha256) { this.hashSha256 = hashSha256; }
    public String getContenidoCifrado() { return contenidoCifrado; }
    public void setContenidoCifrado(String contenidoCifrado) { this.contenidoCifrado = contenidoCifrado; }
    public String getIpRemitente() { return ipRemitente; }
    public void setIpRemitente(String ipRemitente) { this.ipRemitente = ipRemitente; }
    public String getOrigen() { return origen; }
    public void setOrigen(String origen) { this.origen = origen; }
    public String getDestinatario() { return destinatario; }
    public void setDestinatario(String destinatario) { this.destinatario = destinatario; }
    public String getServidorOrigen() { return servidorOrigen; }
    public void setServidorOrigen(String servidorOrigen) { this.servidorOrigen = servidorOrigen; }
}
