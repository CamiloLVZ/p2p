package com.cliente.application.service;

import com.arquitectura.mensajeria.Mensaje;
import com.arquitectura.mensajeria.Respuesta;
import com.arquitectura.mensajeria.enums.Accion;
import com.arquitectura.mensajeria.enums.Estado;
import com.arquitectura.mensajeria.enums.Protocolo;
import com.arquitectura.mensajeria.payload.PayloadFinalizarStream;
import com.arquitectura.mensajeria.payload.PayloadIniciarDescarga;
import com.arquitectura.mensajeria.payload.PayloadIniciarStream;
import com.arquitectura.mensajeria.payload.PayloadSolicitarStream;
import com.cliente.domain.enums.DownloadMode;
import com.cliente.infrastructure.persistence.LocalDocumentRepository;
import com.cliente.domain.model.Document;
import com.cliente.infrastructure.protocol.ProtocolConstants;
import com.cliente.infrastructure.protocol.ServerJsonUtil;
import com.cliente.infrastructure.socket.TcpSocketClient;
import com.cliente.infrastructure.socket.UdpSocketClient;
import javafx.concurrent.Task;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public class FileService {

    private static final Logger LOG = Logger.getLogger(FileService.class.getName());
    private static FileService instance;

    private FileService() {}

    public static FileService getInstance() {
        if (instance == null) instance = new FileService();
        return instance;
    }

    public List<Document> listDocuments() throws Exception {
        Protocolo proto = resolveProtocolo();
        Mensaje<?> msg = ServerJsonUtil.buildRequest(
                Accion.LISTAR_DOCUMENTOS, null,
                ConnectionService.getInstance().getClientId(), proto);

        @SuppressWarnings("unchecked")
        Respuesta<?> resp = ConnectionService.getInstance().send((Mensaje<Object>) msg);

        if (resp.getEstado() == Estado.ERROR
                || resp.getMensaje() == null
                || resp.getMensaje().getPayload() == null) {
            return List.of();
        }
        return ServerJsonUtil.convertList(resp.getMensaje().getPayload(), Document.class);
    }

    public Task<Void> createUploadTask(File file) {
        return createUploadTask(file, null);
    }

    public Task<Void> createUploadTask(File file, String destinatario) {
        return new Task<>() {
            @Override
            protected Void call() throws Exception {
                ConnectionService conn = ConnectionService.getInstance();
                String clientId = conn.getClientId();
                Protocolo proto = resolveProtocolo();
                long fileSize = file.length();
                String ext = getExtension(file.getName());
                String transferId = UUID.randomUUID().toString();

                boolean esTcp = conn.getProtocol() == com.cliente.domain.enums.Protocol.TCP;
                int chunkSize = esTcp ? ProtocolConstants.CHUNK_SIZE : ProtocolConstants.UDP_CHUNK_SIZE;
                long totalChunks = Math.max(1L, (fileSize + chunkSize - 1) / chunkSize);

                updateMessage("Iniciando envío de " + file.getName() + "...");
                updateProgress(0, fileSize);

                PayloadIniciarStream iniciarPayload = new PayloadIniciarStream(
                        transferId, file.getName(), ext, fileSize, totalChunks, chunkSize);
                if (destinatario != null && !destinatario.isBlank()) {
                    iniciarPayload.setClientIdDestino(destinatario);
                }
                Mensaje<PayloadIniciarStream> iniciarMsg = ServerJsonUtil.buildRequest(
                        Accion.INICIAR_STREAM, iniciarPayload, clientId, proto);

                Respuesta<?> iniciarResp = conn.send(iniciarMsg);
                if (iniciarResp.getEstado() == Estado.ERROR) {
                    String err = iniciarResp.getError() != null
                            ? iniciarResp.getError().getMensaje() : "Error desconocido";
                    throw new IOException("El servidor rechazó INICIAR_STREAM: " + err);
                }

                updateMessage("Enviando " + file.getName() + "...");
                String hashFinal = enviarChunks(conn, transferId, file, fileSize, chunkSize, esTcp,
                        (enviados, total) -> {
                            updateProgress(enviados, total);
                            updateMessage(String.format("Enviando %s... %.1f%%",
                                    file.getName(), (enviados * 100.0) / total));
                        });

                if (isCancelled()) return null;

                updateMessage("Verificando integridad de " + file.getName() + "...");
                PayloadFinalizarStream finalizarPayload = new PayloadFinalizarStream(
                        transferId, hashFinal, totalChunks);
                if (destinatario != null && !destinatario.isBlank()) {
                    finalizarPayload.setClientIdDestino(destinatario);
                }
                Mensaje<PayloadFinalizarStream> finalizarMsg = ServerJsonUtil.buildRequest(
                        Accion.FINALIZAR_STREAM, finalizarPayload, clientId, proto);

                Respuesta<?> finalizarResp = conn.send(finalizarMsg);
                if (finalizarResp.getEstado() == Estado.ERROR) {
                    String err = finalizarResp.getError() != null
                            ? finalizarResp.getError().getMensaje() : "Error desconocido";
                    throw new IOException("El servidor rechazó FINALIZAR_STREAM: " + err);
                }

                updateMessage("Completado: " + file.getName());
                updateProgress(fileSize, fileSize);

                String snapshotId   = transferId;
                String snapshotName = file.getName();
                String snapshotExt  = ext;
                long   snapshotSize = fileSize;
                String snapshotHost = conn.getHost();
                int    snapshotPort = conn.getPort();
                String snapshotHash = hashFinal;
                String snapshotUser = conn.getUsername();
                // Solo cachear en H2 si el archivo es pequeño (< 50 MB).
                // Para archivos grandes, guardar el registro sin contenido binario —
                // el servidor ya tiene el archivo y la descarga se hace desde allí.
                final long LIMITE_CACHE_BYTES = 50L * 1024 * 1024;
                byte[] snapshotBytes = null;
                if (fileSize <= LIMITE_CACHE_BYTES) {
                    try {
                        snapshotBytes = Files.readAllBytes(file.toPath());
                    } catch (Exception ex) {
                        LOG.log(Level.WARNING, "No se pudo leer el archivo para guardarlo en H2: " + ex.getMessage(), ex);
                    }
                }
                final byte[] contenidoFinal = snapshotBytes;

                CompletableFuture.runAsync(() -> {
                    try {
                        new LocalDocumentRepository().guardarArchivoEnviado(
                                snapshotId, snapshotUser, null,
                                snapshotName, snapshotExt, null,
                                snapshotHash, null, contenidoFinal,
                                snapshotSize, snapshotHost, snapshotPort);
                    } catch (Exception e) {
                        LOG.log(Level.WARNING, "Error persistiendo archivo en H2: " + e.getMessage(), e);
                    }
                });

                return null;
            }
        };
    }

    private String enviarChunks(ConnectionService conn, String transferId, File file,
                                long fileSize, int chunkSize, boolean esTcp,
                                TcpSocketClient.StreamProgressCallback progressCb) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");

        FileInputStream fis = new FileInputStream(file);
        DigestInputStream dis = new DigestInputStream(fis, digest);

        FileInputStream puente = new FileInputStream(file) {
            @Override public int read() throws IOException                          { return dis.read(); }
            @Override public int read(byte[] b) throws IOException                  { return dis.read(b); }
            @Override public int read(byte[] b, int off, int len) throws IOException { return dis.read(b, off, len); }
            @Override public void close() throws IOException                        { dis.close(); }
        };

        try (puente) {
            if (esTcp) {
                TcpSocketClient tcpClient = new TcpSocketClient();
                tcpClient.connect(conn.getHost(), conn.getPort());
                tcpClient.sendFileStream(transferId, puente, chunkSize, fileSize, progressCb);
            } else {
                UdpSocketClient udpClient = new UdpSocketClient();
                udpClient.connect(conn.getHost(), conn.getPort());
                udpClient.sendFileStreamUdp(transferId, puente, fileSize, progressCb);
            }
        }

        return Base64.getEncoder().encodeToString(digest.digest());
    }

    @FunctionalInterface
    private interface ProgressCallback {
        void update(long done, long total);
    }

    public Task<File> createDownloadTask(Document document, DownloadMode mode, Path destination) {
        return new Task<>() {
            @Override
            protected File call() throws Exception {
                updateMessage("Buscando " + document.getName() + " en caché local...");
                updateProgress(0L, 1L);

                byte[] contenidoLocal = new LocalDocumentRepository()
                        .obtenerContenidoArchivo(document.getId());

                if (contenidoLocal != null && contenidoLocal.length > 0) {
                    return descargarDesdeH2(contenidoLocal, document, mode, destination,
                            this::updateMessage,
                            this::updateProgress);
                }

                return descargarDesdeServidor(document, mode, destination,
                        this::updateMessage,
                        this::updateProgress);
            }
        };
    }

    private File descargarDesdeH2(byte[] contenido, Document document, DownloadMode mode, Path destination,
                                   Consumer<String> onMessage, ProgressCallback onProgress)
            throws Exception {

        String nombreArchivo = resolverNombreArchivo(document.getName(), document.getType());
        Path archivoDestino = destination.resolve(nombreArchivo);

        Files.write(archivoDestino, contenido);

        if (document.getHashSha256() != null && !document.getHashSha256().isBlank()) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(contenido);
            String hashLocal = Base64.getEncoder().encodeToString(digest.digest());
            if (!hashLocal.equals(document.getHashSha256())) {
                Files.deleteIfExists(archivoDestino);
                throw new IOException("El archivo en caché local está corrupto (hash SHA-256 no coincide).");
            }
        }

        if (mode == DownloadMode.HASH && document.getHashSha256() != null && !document.getHashSha256().isBlank()) {
            Files.writeString(destination.resolve(nombreArchivo + ".sha256"), document.getHashSha256());
        }

        long size = contenido.length;
        onMessage.accept("Completado desde caché: " + nombreArchivo);
        onProgress.update(size, size);
        return archivoDestino.toFile();
    }

    private File descargarDesdeServidor(Document document, DownloadMode mode, Path destination,
                                        Consumer<String> onMessage, ProgressCallback onProgress)
            throws Exception {
        ConnectionService conn = ConnectionService.getInstance();
        String clientId = conn.getClientId();
        Protocolo proto = resolveProtocolo();
        boolean esTcp = conn.getProtocol() == com.cliente.domain.enums.Protocol.TCP;

        onMessage.accept("Solicitando " + document.getName() + "...");
        onProgress.update(0L, 1L);

        PayloadSolicitarStream solicitarPayload = new PayloadSolicitarStream(document.getId());
        Mensaje<PayloadSolicitarStream> solicitarMsg = ServerJsonUtil.buildRequest(
                Accion.SOLICITAR_STREAM, solicitarPayload, clientId, proto);

        Respuesta<?> solicitarResp = conn.send(solicitarMsg);
        if (solicitarResp.getEstado() == Estado.ERROR) {
            String err = solicitarResp.getError() != null
                    ? solicitarResp.getError().getMensaje() : "Error desconocido";
            throw new IOException("El servidor rechazó la descarga: " + err);
        }

        PayloadIniciarDescarga meta = ServerJsonUtil.convert(
                solicitarResp.getMensaje().getPayload(), PayloadIniciarDescarga.class);

        String transferId    = meta.getTransferId();
        long   totalBytes    = meta.getTamanoTotal();
        long   totalChunks   = meta.getTotalChunks();
        String hashServidor  = meta.getHashSha256();
        String nombreArchivo = resolverNombreArchivo(meta.getNombreArchivo(), meta.getExtension());

        onMessage.accept("Descargando " + nombreArchivo + "...");
        onProgress.update(0L, totalBytes);

        Path archivoDestino = destination.resolve(nombreArchivo);

        if (esTcp) {
            TcpSocketClient tcpClient = new TcpSocketClient();
            tcpClient.connect(conn.getHost(), conn.getPort());
            tcpClient.receiveFileStream(transferId, totalChunks, archivoDestino, totalBytes,
                    (recibidos, total) -> {
                        onProgress.update(recibidos, total);
                        onMessage.accept(String.format("Descargando %s... %.1f%%",
                                nombreArchivo, (recibidos * 100.0) / total));
                    });
        } else {
            UdpSocketClient udpClient = new UdpSocketClient();
            udpClient.connect(conn.getHost(), conn.getPort());
            udpClient.receiveFileStreamUdp(transferId, totalChunks, archivoDestino, totalBytes,
                    (recibidos, total) -> {
                        onProgress.update(recibidos, total);
                        onMessage.accept(String.format("Descargando %s (UDP)... %.1f%%",
                                nombreArchivo, (recibidos * 100.0) / total));
                    });
        }

        if (hashServidor != null && !hashServidor.isBlank()) {
            onMessage.accept("Verificando integridad...");
            String hashLocal = calcularHash(archivoDestino);
            if (!hashLocal.equals(hashServidor)) {
                Files.deleteIfExists(archivoDestino);
                throw new IOException("El archivo descargado está corrupto " +
                        "(hash SHA-256 no coincide). Se eliminó el archivo parcial.");
            }
        }

        if (mode == DownloadMode.HASH && hashServidor != null && !hashServidor.isBlank()) {
            Files.writeString(destination.resolve(nombreArchivo + ".sha256"), hashServidor);
        }

        onMessage.accept("Completado: " + nombreArchivo);
        onProgress.update(totalBytes, totalBytes);
        return archivoDestino.toFile();
    }

    private String resolverNombreArchivo(String nombreBase, String extension) {
        if (extension != null && !extension.isBlank() && !nombreBase.endsWith("." + extension)) {
            return nombreBase + "." + extension;
        }
        return nombreBase;
    }

    private String calcularHash(Path archivo) throws Exception {
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

    private Protocolo resolveProtocolo() {
        return ConnectionService.getInstance().getProtocol() == com.cliente.domain.enums.Protocol.TCP
                ? Protocolo.TCP : Protocolo.UDP;
    }

    private String getExtension(String name) {
        int dot = name.lastIndexOf('.');
        return (dot >= 0) ? name.substring(dot + 1) : "";
    }
}
