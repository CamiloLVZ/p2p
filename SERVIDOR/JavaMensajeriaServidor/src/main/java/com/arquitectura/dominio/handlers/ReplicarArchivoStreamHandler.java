package com.arquitectura.dominio.handlers;

import com.arquitectura.aplicacion.router.Handler;
import com.arquitectura.aplicacion.transferencia.GestorTransferencias;
import com.arquitectura.mensajeria.ErrorDetalle;
import com.arquitectura.mensajeria.Mensaje;
import com.arquitectura.mensajeria.Metadata;
import com.arquitectura.mensajeria.Respuesta;
import com.arquitectura.mensajeria.enums.Accion;
import com.arquitectura.mensajeria.enums.Estado;
import com.arquitectura.mensajeria.enums.TipoMensaje;
import com.arquitectura.mensajeria.payload.PayloadReplicarArchivoStream;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Maneja el mensaje de control REPLICAR_ARCHIVO_STREAM enviado por un peer.
 *
 * <p>El peer envia primero este mensaje con los metadatos del archivo.
 * El handler registra la transferencia en GestorTransferencias (crea el .tmp en disco)
 * y responde con el transferId para que el peer sepa que puede iniciar el envio de chunks.</p>
 */
public class ReplicarArchivoStreamHandler implements Handler<PayloadReplicarArchivoStream> {

    private static final Logger LOGGER = Logger.getLogger(ReplicarArchivoStreamHandler.class.getName());
    private static final Path DIRECTORIO_DESTINO = Path.of("archivos-recibidos");

    private final GestorTransferencias gestorTransferencias = GestorTransferencias.getInstance();

    @Override
    public Respuesta<?> handle(Mensaje<PayloadReplicarArchivoStream> mensaje) {
        PayloadReplicarArchivoStream payload = mensaje.getPayload();
        if (payload == null || payload.getId() == null || payload.getId().isBlank()) {
            return crearError("PAYLOAD_INVALIDO", "El campo id es obligatorio.");
        }

        String transferId = payload.getId();

        if (gestorTransferencias.existe(transferId)) {
            LOGGER.fine(() -> "Transferencia S2S ya registrada, confirmando: " + transferId);
            return crearRespuestaExitosa(transferId);
        }

        try {
            Files.createDirectories(DIRECTORIO_DESTINO);
            Path rutaTemporal = DIRECTORIO_DESTINO.resolve(transferId + ".tmp");
            Files.createFile(rutaTemporal);

            // Estimacion: si tamano > 0 usar tamano, chunks = 1 (se ajusta con FinalizarStream)
            long totalChunks = payload.getTamano() > 0 ? Math.max(1, payload.getTamano() / (64 * 1024)) : 1;

            gestorTransferencias.registrarS2S(
                    transferId,
                    payload.getNombreArchivo(),
                    payload.getExtension(),
                    payload.getTamano(),
                    totalChunks,
                    rutaTemporal,
                    payload.getServidorOrigen(),
                    payload.getRemitente(),
                    payload.getHashSha256()
            );

            LOGGER.info(() -> "Stream S2S registrado: " + transferId
                    + " | archivo=" + payload.getNombreArchivo()
                    + " | tamano=" + payload.getTamano()
                    + " | origen=" + payload.getServidorOrigen());

            return crearRespuestaExitosa(transferId);

        } catch (IOException e) {
            LOGGER.severe(() -> "Error creando archivo temporal para stream S2S " + transferId + ": " + e.getMessage());
            return crearError("ERROR_DISCO", "No se pudo preparar el archivo destino: " + e.getMessage());
        }
    }

    @Override
    public Class<PayloadReplicarArchivoStream> getPayloadClass() {
        return PayloadReplicarArchivoStream.class;
    }

    private Respuesta<String> crearRespuestaExitosa(String transferId) {
        Mensaje<String> msg = new Mensaje<>();
        msg.setTipo(TipoMensaje.RESPONSE);
        msg.setAccion(Accion.REPLICAR_ARCHIVO_STREAM);
        msg.setMetadata(crearMetadata());
        msg.setPayload(transferId);

        Respuesta<String> resp = new Respuesta<>();
        resp.setEstado(Estado.EXITO);
        resp.setMensaje(msg);
        return resp;
    }

    private Respuesta<?> crearError(String codigo, String detalle) {
        Respuesta<?> resp = new Respuesta<>();
        resp.setEstado(Estado.ERROR);
        resp.setError(new ErrorDetalle(codigo, detalle));
        return resp;
    }

    private Metadata crearMetadata() {
        Metadata m = new Metadata();
        m.setIdMensaje(UUID.randomUUID().toString());
        m.setTimestamp(LocalDateTime.now());
        return m;
    }
}
