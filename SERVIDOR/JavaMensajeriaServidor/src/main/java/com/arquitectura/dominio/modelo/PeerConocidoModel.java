package com.arquitectura.dominio.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "peers_conocidos")
public class PeerConocidoModel {

    /** ID del servidor remoto (ej: "servidor-b") */
    @Id
    @Column(name = "servidor_id", nullable = false, length = 100)
    private String servidorId;

    @Column(nullable = false, length = 100)
    private String host;

    @Column(nullable = false)
    private int puerto;

    @Column(name = "ultima_conexion")
    private LocalDateTime ultimaConexion;

    public PeerConocidoModel() {}

    public PeerConocidoModel(String servidorId, String host, int puerto) {
        this.servidorId = servidorId;
        this.host = host;
        this.puerto = puerto;
        this.ultimaConexion = LocalDateTime.now();
    }

    public String getServidorId() { return servidorId; }
    public void setServidorId(String servidorId) { this.servidorId = servidorId; }

    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }

    public int getPuerto() { return puerto; }
    public void setPuerto(int puerto) { this.puerto = puerto; }

    public LocalDateTime getUltimaConexion() { return ultimaConexion; }
    public void setUltimaConexion(LocalDateTime ultimaConexion) { this.ultimaConexion = ultimaConexion; }
}
