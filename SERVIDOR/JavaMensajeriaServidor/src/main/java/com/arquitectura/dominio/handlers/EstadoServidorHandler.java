package com.arquitectura.dominio.handlers;

import com.arquitectura.aplicacion.router.Handler;
import com.arquitectura.aplicacion.sesion.GestorServidoresPeer;
import com.arquitectura.aplicacion.sesion.GestorSesiones;
import com.arquitectura.mensajeria.Mensaje;
import com.arquitectura.mensajeria.Metadata;
import com.arquitectura.mensajeria.Respuesta;
import com.arquitectura.mensajeria.enums.Accion;
import com.arquitectura.mensajeria.enums.Estado;
import com.arquitectura.mensajeria.enums.TipoMensaje;
import com.arquitectura.mensajeria.payload.PayloadEstadoServidor;
import com.arquitectura.mensajeria.payload.PayloadServidorInfo;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class EstadoServidorHandler implements Handler<Object> {

    private static final Logger LOGGER = Logger.getLogger(EstadoServidorHandler.class.getName());

    /** Momento en que arranco el servidor. Se inicializa una sola vez al cargar la clase. */
    private static final Instant START_TIME = Instant.now();

    @Override
    public Respuesta<?> handle(Mensaje<Object> mensaje) {
        GestorServidoresPeer gestorPeers = GestorServidoresPeer.getInstance();
        GestorSesiones gestorSesiones = GestorSesiones.getInstance();

        long uptimeSeconds = Instant.now().getEpochSecond() - START_TIME.getEpochSecond();

        PayloadEstadoServidor estado = new PayloadEstadoServidor();
        estado.setServidorId(gestorPeers.getServidorId());
        estado.setPuerto(gestorPeers.getServidorPuerto());
        estado.setUptimeSeconds(uptimeSeconds);
        estado.setClientesConectados(gestorSesiones.sesionesActivas());
        estado.setPeersConectados(gestorPeers.obtenerPeersConectados().size());

        List<PayloadServidorInfo> peersInfo = gestorPeers.obtenerPeers().stream()
                .map(p -> {
                    PayloadServidorInfo info = new PayloadServidorInfo();
                    info.setServidorId(p.getConfig().getServidorId());
                    info.setHost(p.getConfig().getHost());
                    info.setPuerto(p.getConfig().getPuerto());
                    info.setEstado(p.getEstado().name());
                    info.setUltimaConexion(p.getUltimaConexion());
                    return info;
                })
                .collect(Collectors.toList());
        estado.setPeers(peersInfo);

        estado.setTimestamp(LocalDateTime.now());

        LOGGER.info(() -> "Estado del servidor consultado | uptime=" + uptimeSeconds + "s"
                + " | clientes=" + estado.getClientesConectados()
                + " | peers=" + estado.getPeersConectados());

        Mensaje<PayloadEstadoServidor> mensajeRespuesta = new Mensaje<>();
        mensajeRespuesta.setTipo(TipoMensaje.RESPONSE);
        mensajeRespuesta.setAccion(Accion.ESTADO_SERVIDOR);
        mensajeRespuesta.setMetadata(crearMetadata());
        mensajeRespuesta.setPayload(estado);

        Respuesta<PayloadEstadoServidor> respuesta = new Respuesta<>();
        respuesta.setEstado(Estado.EXITO);
        respuesta.setMensaje(mensajeRespuesta);
        return respuesta;
    }

    @Override
    public Class<Object> getPayloadClass() {
        return Object.class;
    }

    private Metadata crearMetadata() {
        Metadata metadata = new Metadata();
        metadata.setIdMensaje(UUID.randomUUID().toString());
        metadata.setTimestamp(LocalDateTime.now());
        return metadata;
    }
}
