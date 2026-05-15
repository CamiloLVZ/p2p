package com.arquitectura.aplicacion.transferencia;

import com.arquitectura.dominio.repositorios.ArchivoRecibidoRepository;
import com.arquitectura.dominio.repositorios.JpaArchivoRecibidoRepository;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Registro en memoria de las transferencias de archivos en curso.
 *
 * Cada transferencia tiene un {@link EstadoTransferencia} que contiene:
 * - metadatos del archivo (nombre, extensión, tamaño esperado)
 * - path del archivo temporal en disco (donde se escriben los chunks)
 * - SHA-256 incremental calculado chunk a chunk
 * - cantidad de chunks recibidos
 *
 * Thread-safe: usa ConcurrentHashMap para acceso concurrente.
 */
public class GestorTransferencias {

    private static final Logger LOGGER = Logger.getLogger(GestorTransferencias.class.getName());

    private static final GestorTransferencias INSTANCE = new GestorTransferencias();

    private final ConcurrentHashMap<String, EstadoTransferencia> transferencias = new ConcurrentHashMap<>();

    private GestorTransferencias() {}

    public static GestorTransferencias getInstance() {
        return INSTANCE;
    }

    /**
     * Registra una nueva transferencia entrante.
     *
     * @param transferId    UUID único de la transferencia
     * @param nombreArchivo nombre original del archivo
     * @param extension     extensión sin punto
     * @param tamanoTotal   bytes totales esperados
     * @param totalChunks   cantidad de chunks esperados
     * @param rutaTemporal  path donde se irán escribiendo los chunks
     */
    public void registrar(String transferId, String nombreArchivo, String extension,
                          long tamanoTotal, long totalChunks, Path rutaTemporal) {
        EstadoTransferencia estado = new EstadoTransferencia(
                transferId, nombreArchivo, extension, tamanoTotal, totalChunks, rutaTemporal);
        transferencias.put(transferId, estado);
        LOGGER.info(() -> "Transferencia registrada: " + transferId + " | archivo: " + nombreArchivo);
    }

    /**
     * Registra una transferencia S2S (replicación entre peers).
     * Igual que {@link #registrar} pero marca la transferencia como S2S y guarda el servidorOrigen.
     *
     * @param transferId     UUID único de la transferencia (mismo que en el servidor origen)
     * @param nombreArchivo  nombre original del archivo
     * @param extension      extensión sin punto
     * @param tamanoTotal    bytes totales esperados
     * @param totalChunks    cantidad de chunks esperados
     * @param rutaTemporal   path donde se irán escribiendo los chunks
     * @param servidorOrigen identificador del servidor que originó el archivo
     * @param remitente      usuario remitente del archivo
     * @param hashEsperado   hash SHA-256 en Base64 enviado por el peer (para validación)
     */
    public void registrarS2S(String transferId, String nombreArchivo, String extension,
                             long tamanoTotal, long totalChunks, Path rutaTemporal,
                             String servidorOrigen, String remitente, String hashEsperado) {
        EstadoTransferencia estado = new EstadoTransferencia(
                transferId, nombreArchivo, extension, tamanoTotal, totalChunks, rutaTemporal);
        estado.setEsReplicacionS2S(true);
        estado.setServidorOrigen(servidorOrigen);
        estado.setRemitente(remitente);
        estado.setHashEsperado(hashEsperado);
        transferencias.put(transferId, estado);
        LOGGER.info(() -> "Transferencia S2S registrada: " + transferId
                + " | archivo: " + nombreArchivo + " | origen: " + servidorOrigen);
    }

    /**
     * Finaliza una transferencia S2S recibida:
     * 1. Lee los bytes del .tmp.
     * 2. Calcula hash SHA-256 y contenido cifrado AES.
     * 3. Renombra el .tmp al nombre real (con manejo de colisiones).
     * 4. Persiste en DB via ArchivoRecibidoRepository.
     * 5. Limpia el registro en memoria.
     *
     * Es idempotente: si el .tmp ya no existe o el registro ya está en DB, no hace nada.
     *
     * @param transferId UUID de la transferencia a finalizar
     */
    public void finalizarTransferenciaS2S(String transferId) {
        EstadoTransferencia estado = transferencias.get(transferId);
        if (estado == null) {
            LOGGER.fine(() -> "finalizarTransferenciaS2S: transferencia no encontrada (ya finalizada?): " + transferId);
            return;
        }
        if (!estado.isEsReplicacionS2S()) {
            LOGGER.fine(() -> "finalizarTransferenciaS2S: transferencia no es S2S, ignorando: " + transferId);
            return;
        }

        ArchivoRecibidoRepository repositorio = new JpaArchivoRecibidoRepository();

        // Idempotencia: si ya está en DB, solo limpiamos memoria
        if (repositorio.existePorId(transferId)) {
            LOGGER.info(() -> "finalizarTransferenciaS2S: ya persistido, limpiando memoria: " + transferId);
            eliminar(transferId);
            return;
        }

        Path rutaTemporal = estado.getRutaTemporal();
        if (!Files.exists(rutaTemporal)) {
            LOGGER.warning(() -> "finalizarTransferenciaS2S: archivo .tmp no existe: " + rutaTemporal);
            eliminar(transferId);
            return;
        }

        try {
            // Calcular hash SHA-256 por streaming — NO leer todo el archivo a RAM.
            // Para archivos de 1+ GB, Files.readAllBytes() provoca OOM y además
            // el INSERT en MySQL supera el max_allowed_packet.
            String hashSha256 = sha256Base64Streaming(rutaTemporal);
            // No ciframos contenido para archivos grandes — lo mismo que FinalizarStreamHandler.
            String contenidoCifrado = "";

            Path rutaFinal = resolverRutaFinalS2S(estado);
            Files.move(rutaTemporal, rutaFinal);

            String nombreFinal = rutaFinal.getFileName().toString();
            String nombreBase  = extraerNombreBase(nombreFinal);

            String servidorOrigen = estado.getServidorOrigen() != null
                    ? estado.getServidorOrigen() : "peer-desconocido";
            String remitente = estado.getRemitente() != null
                    ? estado.getRemitente() : "peer-desconocido";

            repositorio.guardar(
                    transferId,
                    remitente,
                    null,
                    nombreBase,
                    estado.getExtension(),
                    rutaFinal.toAbsolutePath().toString(),
                    hashSha256,
                    contenidoCifrado,
                    estado.getTamanoTotal(),
                    LocalDateTime.now(),
                    servidorOrigen
            );

            eliminar(transferId);

            LOGGER.info(() -> "Replicación S2S finalizada: " + transferId
                    + " | archivo: " + nombreFinal
                    + " | bytes: " + bytes.length
                    + " | origen: " + servidorOrigen);

        } catch (IOException e) {
            LOGGER.severe(() -> "finalizarTransferenciaS2S: error de disco para " + transferId + ": " + e.getMessage());
            eliminar(transferId);
        } catch (Exception e) {
            LOGGER.severe(() -> "finalizarTransferenciaS2S: error inesperado para " + transferId + ": " + e.getMessage());
            eliminar(transferId);
        }
    }

    private Path resolverRutaFinalS2S(EstadoTransferencia estado) {
        Path directorio = estado.getRutaTemporal().getParent();
        String nombre   = estado.getNombreArchivo();
        String ext      = estado.getExtension();
        String nombreCompleto = (ext != null && !ext.isBlank() && !nombre.endsWith("." + ext))
                ? nombre + "." + ext : nombre;

        Path candidato = directorio.resolve(nombreCompleto);
        if (!Files.exists(candidato)) return candidato;

        String nombreBase = extraerNombreBase(nombreCompleto);
        int contador = 1;
        while (Files.exists(candidato)) {
            String sufijo = (ext != null && !ext.isBlank())
                    ? nombreBase + " (" + contador + ")." + ext
                    : nombreBase + " (" + contador + ")";
            candidato = directorio.resolve(sufijo);
            contador++;
        }
        return candidato;
    }

    private String extraerNombreBase(String nombre) {
        int dot = nombre.lastIndexOf('.');
        return (dot > 0) ? nombre.substring(0, dot) : nombre;
    }

    /**
     * Calcula el hash SHA-256 de un archivo leyéndolo en chunks de 2 MB.
     * Nunca carga el archivo completo en RAM — seguro para archivos de cualquier tamaño.
     */
    private String sha256Base64Streaming(Path archivo) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[2 * 1024 * 1024];
        try (InputStream is = Files.newInputStream(archivo)) {
            int n;
            while ((n = is.read(buffer)) != -1) {
                digest.update(buffer, 0, n);
            }
        }
        return Base64.getEncoder().encodeToString(digest.digest());
    }

    /**
     * Devuelve el estado de una transferencia activa, o null si no existe.
     */
    public EstadoTransferencia obtener(String transferId) {
        return transferencias.get(transferId);
    }

    /**
     * Elimina la transferencia del registro (al completarse o cancelarse).
     */
    public void eliminar(String transferId) {
        transferencias.remove(transferId);
    }

    /**
     * Verifica si existe una transferencia activa con ese id.
     */
    public boolean existe(String transferId) {
        return transferencias.containsKey(transferId);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Estado interno de una transferencia
    // ─────────────────────────────────────────────────────────────────────────

    public static class EstadoTransferencia {

        private final String transferId;
        private final String nombreArchivo;
        private final String extension;
        private final long tamanoTotal;
        private final long totalChunks;
        private final Path rutaTemporal;
        private final Instant inicio;

        /** SHA-256 incremental — se actualiza con cada chunk recibido. */
        private final MessageDigest digest;

        private long chunksRecibidos = 0;
        private long bytesRecibidos = 0;

        /** true si la transferencia proviene de replicación S2S (peer → peer). */
        private boolean esReplicacionS2S = false;

        /** Servidor que originó el archivo (solo para S2S). */
        private String servidorOrigen;

        /** Usuario remitente original (solo para S2S). */
        private String remitente;

        /** Hash SHA-256 esperado (enviado por el peer en el payload de control). */
        private String hashEsperado;

        /** ID del cliente destino para unicast. null = broadcast. */
        private String clientIdDestino;

        EstadoTransferencia(String transferId, String nombreArchivo, String extension,
                            long tamanoTotal, long totalChunks, Path rutaTemporal) {
            this.transferId = transferId;
            this.nombreArchivo = nombreArchivo;
            this.extension = extension;
            this.tamanoTotal = tamanoTotal;
            this.totalChunks = totalChunks;
            this.rutaTemporal = rutaTemporal;
            this.inicio = Instant.now();
            try {
                this.digest = MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException("SHA-256 no disponible", e);
            }
        }

        /**
         * Actualiza el digest incremental con los bytes de un chunk.
         * Debe llamarse en el mismo orden en que llegan los chunks.
         */
        public synchronized void actualizarDigest(byte[] chunk) {
            digest.update(chunk);
        }

        /**
         * Devuelve el hash SHA-256 calculado hasta el momento, en Base64.
         * NO finaliza el digest — se puede seguir acumulando.
         */
        public synchronized String hashActualBase64() {
            try {
                MessageDigest copia = (MessageDigest) digest.clone();
                return Base64.getEncoder().encodeToString(copia.digest());
            } catch (CloneNotSupportedException e) {
                throw new IllegalStateException("No se pudo clonar el digest", e);
            }
        }

        /**
         * Finaliza el digest y devuelve el hash SHA-256 completo en Base64.
         * Solo debe llamarse una vez al finalizar la transferencia.
         */
        public synchronized String hashFinalBase64() {
            return Base64.getEncoder().encodeToString(digest.digest());
        }

        public synchronized void registrarChunk(int bytesChunk) {
            chunksRecibidos++;
            bytesRecibidos += bytesChunk;
        }

        public boolean estaCompleta() {
            return chunksRecibidos >= totalChunks;
        }

        public void eliminarArchivoParcial() {
            try {
                Files.deleteIfExists(rutaTemporal);
            } catch (IOException e) {
                LOGGER.warning(() -> "No se pudo eliminar archivo parcial: " + rutaTemporal);
            }
        }

        public String getTransferId() { return transferId; }
        public String getNombreArchivo() { return nombreArchivo; }
        public String getExtension() { return extension; }
        public long getTamanoTotal() { return tamanoTotal; }
        public long getTotalChunks() { return totalChunks; }
        public Path getRutaTemporal() { return rutaTemporal; }
        public Instant getInicio() { return inicio; }
        public long getChunksRecibidos() { return chunksRecibidos; }
        public long getBytesRecibidos() { return bytesRecibidos; }

        public boolean isEsReplicacionS2S() { return esReplicacionS2S; }
        public void setEsReplicacionS2S(boolean esReplicacionS2S) { this.esReplicacionS2S = esReplicacionS2S; }

        public String getServidorOrigen() { return servidorOrigen; }
        public void setServidorOrigen(String servidorOrigen) { this.servidorOrigen = servidorOrigen; }

        public String getRemitente() { return remitente; }
        public void setRemitente(String remitente) { this.remitente = remitente; }

        public String getHashEsperado() { return hashEsperado; }
        public void setHashEsperado(String hashEsperado) { this.hashEsperado = hashEsperado; }

        public String getClientIdDestino() { return clientIdDestino; }
        public void setClientIdDestino(String clientIdDestino) { this.clientIdDestino = clientIdDestino; }
    }
}
