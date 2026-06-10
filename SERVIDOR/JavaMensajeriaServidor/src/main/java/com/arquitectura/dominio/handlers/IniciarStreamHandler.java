package com.arquitectura.dominio.handlers;

import com.arquitectura.aplicacion.ContextoSolicitud;
import com.arquitectura.aplicacion.router.Handler;
import com.arquitectura.aplicacion.sesion.GestorSesiones;
import com.arquitectura.aplicacion.sesion.ResultadoValidacionSesion;
import com.arquitectura.aplicacion.transferencia.GestorTransferencias;
import com.arquitectura.mensajeria.ErrorDetalle;
import com.arquitectura.mensajeria.Mensaje;
import com.arquitectura.mensajeria.Metadata;
import com.arquitectura.mensajeria.Respuesta;
import com.arquitectura.mensajeria.enums.Accion;
import com.arquitectura.mensajeria.enums.Estado;
import com.arquitectura.mensajeria.enums.TipoMensaje;
import com.arquitectura.mensajeria.payload.PayloadIniciarStream;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Maneja el mensaje de control INICIAR_STREAM.
 *
 * Registra la transferencia en el GestorTransferencias y crea el archivo
 * temporal vacío en disco donde se irán escribiendo los chunks.
 * Responde con el transferId confirmando que el servidor está listo.
 */
public class IniciarStreamHandler implements Handler<PayloadIniciarStream> {

    private static final Logger LOGGER = Logger.getLogger(IniciarStreamHandler.class.getName());
    private static final Path DIRECTORIO_DESTINO = Path.of("archivos-recibidos");

    private final GestorSesiones gestorSesiones = GestorSesiones.getInstance();
    private final GestorTransferencias gestorTransferencias = GestorTransferencias.getInstance();

    @Override
    public Respuesta<?> handle(Mensaje<PayloadIniciarStream> mensaje) {
        PayloadIniciarStream payload = mensaje.getPayload();
        String remitente = resolverRemitente(mensaje);

        ResultadoValidacionSesion validacion = gestorSesiones.validarSesion(
                remitente,
                ContextoSolicitud.obtenerIpRemitente(),
                puertoRemitente(),
                ContextoSolicitud.obtenerProtocolo()
        );
        if (!validacion.exito()) {
            return crearError("SESION_INVALIDA", validacion.mensaje());
        }

        String transferId = payload.getTransferId();
        if (transferId == null || transferId.isBlank()) {
            return crearError("TRANSFER_ID_REQUERIDO", "El campo transferId es obligatorio.");
        }
        if (gestorTransferencias.existe(transferId)) {
            return crearError("TRANSFER_ID_DUPLICADO", "Ya existe una transferencia activa con ese id.");
        }

        try {
            Files.createDirectories(DIRECTORIO_DESTINO);
            Path rutaTemporal = DIRECTORIO_DESTINO.resolve(transferId + ".tmp");

            // Crea el archivo vacío — los chunks se irán apendando
            Files.createFile(rutaTemporal);

            gestorTransferencias.registrar(
                    transferId,
                    payload.getNombreArchivo(),
                    payload.getExtension(),
                    payload.getTamanoTotal(),
                    payload.getTotalChunks(),
                    rutaTemporal
            );

            // Propagar clientIdDestino para routing unicast al finalizar
            if (payload.getClientIdDestino() != null && !payload.getClientIdDestino().isBlank()) {
                GestorTransferencias.EstadoTransferencia estado = gestorTransferencias.obtener(transferId);
                if (estado != null) {
                    estado.setClientIdDestino(payload.getClientIdDestino());
                }
            }

            LOGGER.info(() -> "Stream iniciado: " + transferId
                    + " | archivo: " + payload.getNombreArchivo()
                    + " | tamaño: " + payload.getTamanoTotal() + " bytes"
                    + " | chunks: " + payload.getTotalChunks()
                    + " | remitente: " + remitente);

            return crearRespuestaExitosa(transferId);

        } catch (IOException e) {
            LOGGER.severe(() -> "Error creando archivo temporal para transferencia " + transferId + ": " + e.getMessage());
            return crearError("ERROR_DISCO", "No se pudo preparar el archivo destino: " + e.getMessage());
        }
    }

    @Override
    public Class<PayloadIniciarStream> getPayloadClass() {
        return PayloadIniciarStream.class;
    }

    private Respuesta<String> crearRespuestaExitosa(String transferId) {
        Mensaje<String> msg = new Mensaje<>();
        msg.setTipo(TipoMensaje.RESPONSE);
        msg.setAccion(Accion.INICIAR_STREAM);
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

    private String resolverRemitente(Mensaje<?> mensaje) {
        if (mensaje.getMetadata() != null && mensaje.getMetadata().getClientId() != null) {
            return mensaje.getMetadata().getClientId();
        }
        return "desconocido";
    }

    private int puertoRemitente() {
        Integer p = ContextoSolicitud.obtenerPuertoRemitente();
        return p == null ? -1 : p;
    }
}
