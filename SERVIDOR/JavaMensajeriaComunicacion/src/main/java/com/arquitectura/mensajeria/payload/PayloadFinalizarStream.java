package com.arquitectura.mensajeria.payload;

/**
 * Payload del mensaje de control FINALIZAR_STREAM.
 *
 * El cliente lo envía después de mandar todos los chunks binarios.
 * El servidor valida el hash SHA-256 del archivo completo y persiste
 * el registro en base de datos.
 */
public class PayloadFinalizarStream {

    /** Identificador único de la transferencia, debe coincidir con el INICIAR_STREAM. */
    private String transferId;

    /**
     * Hash SHA-256 del archivo completo calculado por el cliente,
     * codificado en Base64.
     * El servidor lo compara con el que calculó incrementalmente.
     */
    private String hashSha256;

    /** Cantidad de chunks efectivamente enviados. */
    private long chunksEnviados;

    /**
     * ID del cliente destino para envío unicast (propagado desde INICIAR_STREAM).
     * Si es null, el servidor hace broadcast al finalizar.
     */
    private String clientIdDestino;

    public PayloadFinalizarStream() {}

    public PayloadFinalizarStream(String transferId, String hashSha256, long chunksEnviados) {
        this.transferId = transferId;
        this.hashSha256 = hashSha256;
        this.chunksEnviados = chunksEnviados;
    }

    public String getTransferId() { return transferId; }
    public void setTransferId(String transferId) { this.transferId = transferId; }

    public String getHashSha256() { return hashSha256; }
    public void setHashSha256(String hashSha256) { this.hashSha256 = hashSha256; }

    public long getChunksEnviados() { return chunksEnviados; }
    public void setChunksEnviados(long chunksEnviados) { this.chunksEnviados = chunksEnviados; }

    public String getClientIdDestino() { return clientIdDestino; }
    public void setClientIdDestino(String clientIdDestino) { this.clientIdDestino = clientIdDestino; }
}
