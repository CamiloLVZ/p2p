package com.arquitectura.dominio.handlers;

import com.arquitectura.aplicacion.router.Handler;
import com.arquitectura.aplicacion.sesion.GestorServidoresPeer;
import com.arquitectura.mensajeria.Mensaje;
import com.arquitectura.mensajeria.Metadata;
import com.arquitectura.mensajeria.Respuesta;
import com.arquitectura.mensajeria.enums.Accion;
import com.arquitectura.mensajeria.enums.Estado;
import com.arquitectura.mensajeria.enums.TipoMensaje;
import com.arquitectura.mensajeria.payload.PayloadRegistrarServidor;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.logging.Logger;

public class DesconectarServidorHandler implements Handler<PayloadRegistrarServidor> {

    private static final Logger LOGGER = Logger.getLogger(DesconectarServidorHandler.class.getName());

    @Override
    public Respuesta<?> handle(Mensaje<PayloadRegistrarServidor> mensaje) {
        PayloadRegistrarServidor payload = mensaje.getPayload();
        if (payload == null || payload.getServidorId() == null || payload.getServidorId().isBlank()) {
            Respuesta<?> error = new Respuesta<>();
            error.setEstado(Estado.ERROR);
            return error;
        }

        String servidorId = payload.getServidorId();
        GestorServidoresPeer gestor = GestorServidoresPeer.getInstance();

        // Invalidar el cache de clientes del peer desconectado
        gestor.invalidarCacheClientes(servidorId);

        // Marcar el peer como desconectado (OPEN = sin conexion activa)
        gestor.marcarPeerDesconectado(servidorId);

        LOGGER.info(() -> "Peer desconectado: " + servidorId
                + " | cache de clientes invalidado");

        Mensaje<String> mensajeRespuesta = new Mensaje<>();
        mensajeRespuesta.setTipo(TipoMensaje.RESPONSE);
        mensajeRespuesta.setAccion(Accion.DESCONECTAR_SERVIDOR);
        mensajeRespuesta.setMetadata(crearMetadata());
        mensajeRespuesta.setPayload("Servidor desconectado: " + servidorId);

        Respuesta<String> respuesta = new Respuesta<>();
        respuesta.setEstado(Estado.EXITO);
        respuesta.setMensaje(mensajeRespuesta);
        return respuesta;
    }

    @Override
    public Class<PayloadRegistrarServidor> getPayloadClass() {
        return PayloadRegistrarServidor.class;
    }

    private Metadata crearMetadata() {
        Metadata metadata = new Metadata();
        metadata.setIdMensaje(UUID.randomUUID().toString());
        metadata.setTimestamp(LocalDateTime.now());
        return metadata;
    }
}
