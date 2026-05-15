package com.cliente.domain.model;

public class Document {
    private String id;
    private String name;
    private long size;
    private String type;
    private String date;
    private String hashSha256;
    private String ipRemitente;
    private String origen;

    public Document() {}

    public Document(String id, String name, long size, String type, String date) {
        this.id = id;
        this.name = name;
        this.size = size;
        this.type = type;
        this.date = date;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public long getSize() { return size; }
    public void setSize(long size) { this.size = size; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }
    public String getHashSha256() { return hashSha256; }
    public void setHashSha256(String hashSha256) { this.hashSha256 = hashSha256; }
    public String getIpRemitente() { return ipRemitente; }
    public void setIpRemitente(String ipRemitente) { this.ipRemitente = ipRemitente; }
    public String getOrigen() { return origen; }
    public void setOrigen(String origen) { this.origen = origen; }

    public String getFormattedSize() {
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return String.format("%.1f KB", size / 1024.0);
        if (size < 1024L * 1024 * 1024) return String.format("%.1f MB", size / (1024.0 * 1024));
        return String.format("%.2f GB", size / (1024.0 * 1024 * 1024));
    }
}
