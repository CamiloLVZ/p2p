package com.arquitectura.dominio.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "archivos")
public class ArchivoRecibidoModel {

    @Id
    @Column(nullable = false, length = 36, updatable = false)
    private String id;

    @Column(nullable = false, length = 255)
    private String remitente;

    @Column(name = "ip_remitente", length = 45)
    private String ipRemitente;

    @Column(name = "nombre_archivo", nullable = false, length = 255)
    private String nombreArchivo;

    @Column(length = 50)
    private String extension;

    @Column(name = "ruta_archivo", nullable = false, columnDefinition = "TEXT")
    private String rutaArchivo;

    @Column(name = "hash_sha256", nullable = false, length = 88)
    private String hashSha256;

    @Column(name = "contenido_cifrado", columnDefinition = "LONGTEXT")
    private String contenidoCifrado;

    @Column(nullable = false)
    private long tamano;

    @Column(name = "fecha_recepcion", nullable = false)
    private LocalDateTime fechaRecepcion;

    @Column(name = "servidor_origen", length = 100)
    private String servidorOrigen;  // null for locally-originated records

    @Column(name = "destinatario", length = 150)
    private String destinatario;  // null = broadcast; valor = unicast

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getRemitente() {
        return remitente;
    }

    public void setRemitente(String remitente) {
        this.remitente = remitente;
    }

    public String getIpRemitente() {
        return ipRemitente;
    }

    public void setIpRemitente(String ipRemitente) {
        this.ipRemitente = ipRemitente;
    }

    public String getNombreArchivo() {
        return nombreArchivo;
    }

    public void setNombreArchivo(String nombreArchivo) {
        this.nombreArchivo = nombreArchivo;
    }

    public String getExtension() {
        return extension;
    }

    public void setExtension(String extension) {
        this.extension = extension;
    }

    public String getRutaArchivo() {
        return rutaArchivo;
    }

    public void setRutaArchivo(String rutaArchivo) {
        this.rutaArchivo = rutaArchivo;
    }

    public String getHashSha256() {
        return hashSha256;
    }

    public void setHashSha256(String hashSha256) {
        this.hashSha256 = hashSha256;
    }

    public String getContenidoCifrado() {
        return contenidoCifrado;
    }

    public void setContenidoCifrado(String contenidoCifrado) {
        this.contenidoCifrado = contenidoCifrado;
    }

    public long getTamano() {
        return tamano;
    }

    public void setTamano(long tamano) {
        this.tamano = tamano;
    }

    public LocalDateTime getFechaRecepcion() {
        return fechaRecepcion;
    }

    public void setFechaRecepcion(LocalDateTime fechaRecepcion) {
        this.fechaRecepcion = fechaRecepcion;
    }

    public String getServidorOrigen() {
        return servidorOrigen;
    }

    public void setServidorOrigen(String servidorOrigen) {
        this.servidorOrigen = servidorOrigen;
    }

    public String getDestinatario() {
        return destinatario;
    }

    public void setDestinatario(String destinatario) {
        this.destinatario = destinatario;
    }
}
