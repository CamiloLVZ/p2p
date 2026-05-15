package com.arquitectura.aplicacion.transferencia;

import java.nio.file.Path;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Logger;

/**
 * Registro en memoria de las descargas autorizadas pendientes de envío.
 *
 * También actúa como router de ACKs UDP:
 * el loop principal de UdpProtocoloTransporte deposita los ACKs aquí
 * (en vez de competir por socket.receive()), y StreamEmisorUdp los consume.
 */
public class GestorDescargasActivas {

    private static final Logger LOGGER = Logger.getLogger(GestorDescargasActivas.class.getName());
    private static final GestorDescargasActivas INSTANCE = new GestorDescargasActivas();

    private final ConcurrentHashMap<String, DescargaAutorizada> descargas = new ConcurrentHashMap<>();

    /**
     * Cola global de ACKs UDP de descarga.
     * El loop de UdpProtocoloTransporte deposita aquí los datagramas 0x01.
     * StreamEmisorUdp los consume con take() o poll().
     */
    private final BlockingQueue<byte[]> colaAcks = new LinkedBlockingQueue<>();

    private GestorDescargasActivas() {}

    public static GestorDescargasActivas getInstance() { return INSTANCE; }

    public void registrar(String transferId, Path rutaArchivo, int chunkSize) {
        descargas.put(transferId, new DescargaAutorizada(transferId, rutaArchivo, chunkSize));
        LOGGER.info(() -> "Descarga registrada: " + transferId + " -> " + rutaArchivo);
    }

    public DescargaAutorizada obtener(String transferId) {
        return descargas.get(transferId);
    }

    public void eliminar(String transferId) {
        descargas.remove(transferId);
    }

    public boolean existe(String transferId) {
        return descargas.containsKey(transferId);
    }

    /**
     * Deposita un datagrama ACK/NACK recibido por el loop principal.
     * El emisor activo lo consumirá via recibirAck().
     */
    public void depositarAck(byte[] datagramaAck) {
        colaAcks.offer(datagramaAck);
    }

    /**
     * Espera hasta timeoutMs ms por un ACK. Devuelve null si vence el timeout.
     */
    public byte[] recibirAck(long timeoutMs) throws InterruptedException {
        return colaAcks.poll(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    /** Vacía la cola de ACKs (al finalizar una transferencia). */
    public void limpiarAcks() {
        colaAcks.clear();
    }

    // ─────────────────────────────────────────────────────────────────────────

    public static class DescargaAutorizada {
        private final String transferId;
        private final Path rutaArchivo;
        private final int chunkSize;

        DescargaAutorizada(String transferId, Path rutaArchivo, int chunkSize) {
            this.transferId = transferId;
            this.rutaArchivo = rutaArchivo;
            this.chunkSize = chunkSize;
        }

        public String getTransferId()  { return transferId; }
        public Path getRutaArchivo()   { return rutaArchivo; }
        public int getChunkSize()      { return chunkSize; }
    }
}
