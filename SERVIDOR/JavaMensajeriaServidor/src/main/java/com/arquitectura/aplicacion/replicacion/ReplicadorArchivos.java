package com.arquitectura.aplicacion.replicacion;

import com.arquitectura.aplicacion.sesion.ConexionPeer;
import com.arquitectura.aplicacion.sesion.GestorServidoresPeer;
import com.arquitectura.dominio.modelo.ArchivoRecibidoModel;
import com.arquitectura.mensajeria.Mensaje;
import com.arquitectura.mensajeria.Metadata;
import com.arquitectura.mensajeria.enums.Accion;
import com.arquitectura.mensajeria.enums.TipoMensaje;
import com.arquitectura.mensajeria.payload.PayloadReplicarArchivo;
import com.arquitectura.mensajeria.payload.PayloadReplicarArchivoStream;

import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

/**
 * Decides replication strategy for files after they are persisted locally.
 *
 * <ul>
 *   <li>If {@code contenidoCifrado} is present (small file encoded as Base64): replicate via JSON.</li>
 *   <li>Otherwise (large stream file): notify peers to prepare and stream raw bytes via S2S TCP.</li>
 * </ul>
 *
 * All replication is asynchronous — callers must not block on this class.
 * {@link GestorServidoresPeer#enviarATodos} and {@link GestorServidoresPeer#enviarAPeer}
 * are already fire-and-forget (executor-backed).
 */
public class ReplicadorArchivos {

    private static final Logger LOGGER = Logger.getLogger(ReplicadorArchivos.class.getName());
    private static final ExecutorService STREAM_EXECUTOR = Executors.newCachedThreadPool();

    /**
     * Called after a file is persisted locally.
     *
     * @param archivo     the persisted {@link ArchivoRecibidoModel}
     * @param servidorId  this server's own ID
     */
    public void replicar(ArchivoRecibidoModel archivo, String servidorId) {
        GestorServidoresPeer peers = GestorServidoresPeer.getInstance();
        if (peers.obtenerPeersConectados().isEmpty()) return;
        replicarPorStream(archivo, servidorId, peers);
        
    }
    // -------------------------------------------------------------------------
    // Stream replication (large files)
    // -------------------------------------------------------------------------

    private void replicarPorStream(ArchivoRecibidoModel archivo, String servidorId, GestorServidoresPeer peers) {
        PayloadReplicarArchivoStream payload = new PayloadReplicarArchivoStream();
        payload.setId(archivo.getId());
        payload.setNombreArchivo(archivo.getNombreArchivo());
        payload.setExtension(archivo.getExtension());
        payload.setTamano(archivo.getTamano());
        payload.setHashSha256(archivo.getHashSha256());
        payload.setServidorOrigen(servidorId);
        payload.setRemitente(archivo.getRemitente());

        for (ConexionPeer peer : peers.obtenerPeersConectados()) {
            Mensaje<PayloadReplicarArchivoStream> msg = buildMensaje(Accion.REPLICAR_ARCHIVO_STREAM, payload);
            boolean ok = peers.enviarAPeer(peer.getConfig().getServidorId(), msg);
            if (ok) {
                streamArchivoPeer(archivo, peer);
            } else {
                LOGGER.warning(() -> "No se pudo notificar stream a peer: " + peer.getConfig().getServidorId());
            }
        }
    }

    private void streamArchivoPeer(ArchivoRecibidoModel archivo, ConexionPeer peer) {
        STREAM_EXECUTOR.submit(() -> {
            Path rutaArchivo = Path.of(archivo.getRutaArchivo());
            if (!Files.exists(rutaArchivo)) {
                LOGGER.warning(() -> "Archivo no encontrado en disco para stream S2S: " + rutaArchivo);
                return;
            }

            String host = peer.getConfig().getHost();
            int puerto = peer.getConfig().getPuerto();
            int chunkSize = 2 * 1024 * 1024; // 2MB TCP chunks

            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(host, puerto), 5000);
                socket.setSoTimeout(0);

                OutputStream os = socket.getOutputStream();
                InputStream is = socket.getInputStream();

                // Send stream signal byte (same as TcpSocketClient: 0x02)
                os.write(0x02);
                os.flush();

                String transferId = archivo.getId();

                try (FileInputStream fis = new FileInputStream(rutaArchivo.toFile())) {
                    byte[] buffer = new byte[chunkSize];
                    long chunkIndex = 0;
                    int read;

                    while ((read = fis.read(buffer)) != -1) {
                        ByteBuffer header = ByteBuffer.allocate(36 + 8 + 4);
                        header.put(padTransferId(transferId));
                        header.putLong(chunkIndex);
                        header.putInt(read);

                        os.write(header.array());
                        os.write(buffer, 0, read);
                        os.flush();

                        int ack = is.read();
                        if (ack != 0x01) {
                            final long ci = chunkIndex;
                            LOGGER.warning(() -> "Peer rechazó chunk " + ci + " en stream S2S a " + host);
                            return;
                        }
                        chunkIndex++;
                    }
                }

                LOGGER.info(() -> "Stream S2S completado: " + archivo.getNombreArchivo() + " → " + host + ":" + puerto);

            } catch (Exception e) {
                LOGGER.warning(() -> "Error en stream S2S a " + host + ":" + puerto + " — " + e.getMessage());
            }
        });
    }

    private byte[] padTransferId(String transferId) {
        byte[] src = transferId.getBytes(StandardCharsets.UTF_8);
        byte[] padded = new byte[36];
        System.arraycopy(src, 0, padded, 0, Math.min(src.length, 36));
        return padded;
    }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    private <T> Mensaje<T> buildMensaje(Accion accion, T payload) {
        Metadata meta = new Metadata();
        meta.setIdMensaje(UUID.randomUUID().toString());
        meta.setTimestamp(LocalDateTime.now());

        Mensaje<T> msg = new Mensaje<>();
        msg.setTipo(TipoMensaje.REQUEST);
        msg.setAccion(accion);
        msg.setMetadata(meta);
        msg.setPayload(payload);
        return msg;
    }
}
