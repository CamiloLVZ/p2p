package com.arquitectura.mensajeria.payload;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Payload S2S enviado por un servidor a un peer recién conectado/reconectado
 * para que éste se ponga al día con el historial completo de mensajes y archivos.
 *
 * <p>Los receptores aplican idempotencia via {@code existePorId} antes de persistir.</p>
 */
public class PayloadSincronizarEstado {

    private String servidorOrigen;
    private List<MensajeSync> mensajes;
    private List<ArchivoSync> archivos;

    public PayloadSincronizarEstado() {}

    public String getServidorOrigen() { return servidorOrigen; }
    public void setServidorOrigen(String servidorOrigen) { this.servidorOrigen = servidorOrigen; }

    public List<MensajeSync> getMensajes() { return mensajes; }
    public void setMensajes(List<MensajeSync> mensajes) { this.mensajes = mensajes; }

    public List<ArchivoSync> getArchivos() { return archivos; }
    public void setArchivos(List<ArchivoSync> archivos) { this.archivos = archivos; }

    // -------------------------------------------------------------------------
    // DTOs internos — espejo plano de los modelos JPA (sin dependencias Jakarta)
    // -------------------------------------------------------------------------

    public static class MensajeSync {
        private String id;
        private String autor;
        private String ipRemitente;
        private String contenido;
        private String hashSha256;
        private String contenidoCifrado;
        private LocalDateTime fechaEnvio;
        private String servidorOrigen;
        private String destinatario;

        public MensajeSync() {}

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getAutor() { return autor; }
        public void setAutor(String autor) { this.autor = autor; }

        public String getIpRemitente() { return ipRemitente; }
        public void setIpRemitente(String ipRemitente) { this.ipRemitente = ipRemitente; }

        public String getContenido() { return contenido; }
        public void setContenido(String contenido) { this.contenido = contenido; }

        public String getHashSha256() { return hashSha256; }
        public void setHashSha256(String hashSha256) { this.hashSha256 = hashSha256; }

        public String getContenidoCifrado() { return contenidoCifrado; }
        public void setContenidoCifrado(String contenidoCifrado) { this.contenidoCifrado = contenidoCifrado; }

        public LocalDateTime getFechaEnvio() { return fechaEnvio; }
        public void setFechaEnvio(LocalDateTime fechaEnvio) { this.fechaEnvio = fechaEnvio; }

        public String getServidorOrigen() { return servidorOrigen; }
        public void setServidorOrigen(String servidorOrigen) { this.servidorOrigen = servidorOrigen; }

        public String getDestinatario() { return destinatario; }
        public void setDestinatario(String destinatario) { this.destinatario = destinatario; }
    }

    public static class ArchivoSync {
        private String id;
        private String remitente;
        private String ipRemitente;
        private String nombreArchivo;
        private String extension;
        private String rutaArchivo;
        private String hashSha256;
        private String contenidoCifrado;
        private long tamano;
        private LocalDateTime fechaRecepcion;
        private String servidorOrigen;
        private String destinatario;

        public ArchivoSync() {}

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getRemitente() { return remitente; }
        public void setRemitente(String remitente) { this.remitente = remitente; }

        public String getIpRemitente() { return ipRemitente; }
        public void setIpRemitente(String ipRemitente) { this.ipRemitente = ipRemitente; }

        public String getNombreArchivo() { return nombreArchivo; }
        public void setNombreArchivo(String nombreArchivo) { this.nombreArchivo = nombreArchivo; }

        public String getExtension() { return extension; }
        public void setExtension(String extension) { this.extension = extension; }

        public String getRutaArchivo() { return rutaArchivo; }
        public void setRutaArchivo(String rutaArchivo) { this.rutaArchivo = rutaArchivo; }

        public String getHashSha256() { return hashSha256; }
        public void setHashSha256(String hashSha256) { this.hashSha256 = hashSha256; }

        public String getContenidoCifrado() { return contenidoCifrado; }
        public void setContenidoCifrado(String contenidoCifrado) { this.contenidoCifrado = contenidoCifrado; }

        public long getTamano() { return tamano; }
        public void setTamano(long tamano) { this.tamano = tamano; }

        public LocalDateTime getFechaRecepcion() { return fechaRecepcion; }
        public void setFechaRecepcion(LocalDateTime fechaRecepcion) { this.fechaRecepcion = fechaRecepcion; }

        public String getServidorOrigen() { return servidorOrigen; }
        public void setServidorOrigen(String servidorOrigen) { this.servidorOrigen = servidorOrigen; }

        public String getDestinatario() { return destinatario; }
        public void setDestinatario(String destinatario) { this.destinatario = destinatario; }
    }
}
