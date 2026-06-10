package com.arquitectura.dominio.handlers;

import com.arquitectura.aplicacion.router.Handler;
import com.arquitectura.dominio.repositorios.JpaMensajeRepository;
import com.arquitectura.dominio.repositorios.MensajeRepository;
import com.arquitectura.mensajeria.Mensaje;
import com.arquitectura.mensajeria.Metadata;
import com.arquitectura.mensajeria.Respuesta;
import com.arquitectura.mensajeria.enums.Accion;
import com.arquitectura.mensajeria.enums.Estado;
import com.arquitectura.mensajeria.enums.TipoMensaje;
import com.arquitectura.mensajeria.payload.PayloadSincronizarEstado;
import com.arquitectura.mensajeria.payload.PayloadSincronizarEstado.ArchivoSync;
import com.arquitectura.mensajeria.payload.PayloadSincronizarEstado.MensajeSync;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Handler S2S: recibe el estado completo (mensajes + archivos) de un peer
 * y persiste lo que aún no existe localmente.
 *
 * <p>La idempotencia es garantizada por {@code existePorId} en ambos repositorios.
 * Si un registro ya existe localmente, se ignora silenciosamente — sin excepción,
 * sin duplicado.</p>
 *
 * <p>Casos de uso:
 * <ul>
 *   <li>Servidor {@code B} se reconecta tras estar caído → recibe el historial que le falta.</li>
 *   <li>Servidor {@code C} se enciende por primera vez → recibe todo el estado existente.</li>
 * </ul>
 * </p>
 */
public class SincronizarEstadoHandler implements Handler<PayloadSincronizarEstado> {

    private static final Logger LOGGER = Logger.getLogger(SincronizarEstadoHandler.class.getName());

    private final MensajeRepository mensajeRepo = new JpaMensajeRepository();

    @Override
    public Respuesta<?> handle(Mensaje<PayloadSincronizarEstado> mensaje) {
        PayloadSincronizarEstado payload = mensaje.getPayload();
        if (payload == null) {
            Respuesta<?> error = new Respuesta<>();
            error.setEstado(Estado.ERROR);
            return error;
        }

        String origen = payload.getServidorOrigen() != null ? payload.getServidorOrigen() : "peer-desconocido";
        int mensajesNuevos = 0;

        // ---- Sincronizar mensajes ----
        List<MensajeSync> mensajes = payload.getMensajes();
        if (mensajes != null) {
            for (MensajeSync m : mensajes) {
                if (m.getId() == null || m.getId().isBlank()) continue;
                if (mensajeRepo.existePorId(m.getId())) continue; // idempotencia

                LocalDateTime fecha = m.getFechaEnvio() != null ? m.getFechaEnvio() : LocalDateTime.now();
                mensajeRepo.guardar(
                        m.getId(),
                        m.getAutor() != null ? m.getAutor() : "desconocido",
                        m.getIpRemitente(),
                        m.getContenido() != null ? m.getContenido() : "",
                        m.getHashSha256() != null ? m.getHashSha256() : "",
                        m.getContenidoCifrado() != null ? m.getContenidoCifrado() : "",
                        fecha,
                        m.getServidorOrigen() != null ? m.getServidorOrigen() : origen,
                        m.getDestinatario()
                );
                mensajesNuevos++;
            }
        }

        // ---- Archivos ----
        // Los metadatos de archivos NO se persisten aquí intencionalmente.
        // El servidor origen envía los bytes físicos vía stream S2S (REPLICAR_ARCHIVO_STREAM)
        // inmediatamente después de este sync. GestorTransferencias.finalizarTransferenciaS2S()
        // es quien persiste en DB una vez que el stream completa con éxito.
        // Esto garantiza que la DB solo tenga registros con el archivo físico real en disco.
        List<ArchivoSync> archivos = payload.getArchivos();
        int archivosEsperados = archivos != null ? archivos.size() : 0;

        final int totalMensajes = mensajesNuevos;
        final int totalArchivos = archivosEsperados;
        LOGGER.info(() -> "Sincronización recibida de " + origen
                + " | mensajes nuevos=" + totalMensajes
                + " | archivos en tránsito (stream S2S)=" + totalArchivos);

        return crearRespuestaExito(mensajesNuevos, archivosEsperados);
    }

    @Override
    public Class<PayloadSincronizarEstado> getPayloadClass() {
        return PayloadSincronizarEstado.class;
    }

    private Respuesta<String> crearRespuestaExito(int mensajes, int archivos) {
        Metadata meta = new Metadata();
        meta.setIdMensaje(UUID.randomUUID().toString());
        meta.setTimestamp(LocalDateTime.now());

        Mensaje<String> mensajeResp = new Mensaje<>();
        mensajeResp.setTipo(TipoMensaje.RESPONSE);
        mensajeResp.setAccion(Accion.SINCRONIZAR_ESTADO);
        mensajeResp.setMetadata(meta);
        mensajeResp.setPayload("Sync OK: " + mensajes + " mensajes, " + archivos + " archivos persistidos");

        Respuesta<String> respuesta = new Respuesta<>();
        respuesta.setEstado(Estado.EXITO);
        respuesta.setMensaje(mensajeResp);
        return respuesta;
    }
}
