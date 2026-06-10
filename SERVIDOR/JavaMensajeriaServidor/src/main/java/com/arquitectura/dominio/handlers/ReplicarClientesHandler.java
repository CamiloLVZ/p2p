package com.arquitectura.dominio.handlers;

import com.arquitectura.aplicacion.router.Handler;
import com.arquitectura.aplicacion.sesion.GestorServidoresPeer;
import com.arquitectura.mensajeria.Mensaje;
import com.arquitectura.mensajeria.Metadata;
import com.arquitectura.mensajeria.Respuesta;
import com.arquitectura.mensajeria.enums.Accion;
import com.arquitectura.mensajeria.enums.Estado;
import com.arquitectura.mensajeria.enums.TipoMensaje;
import com.arquitectura.mensajeria.payload.PayloadReplicarClientes;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

public class ReplicarClientesHandler implements Handler<PayloadReplicarClientes> {

    private static final Logger LOGGER = Logger.getLogger(ReplicarClientesHandler.class.getName());

    @Override
    public Respuesta<?> handle(Mensaje<PayloadReplicarClientes> mensaje) {
        PayloadReplicarClientes payload = mensaje.getPayload();
        if (payload == null || payload.getServidorOrigen() == null) {
            Respuesta<?> error = new Respuesta<>();
            error.setEstado(Estado.ERROR);
            return error;
        }

        String servidorOrigen = payload.getServidorOrigen();
        int cantidad = payload.getClientes() != null ? payload.getClientes().size() : 0;

        GestorServidoresPeer gestorPeers = GestorServidoresPeer.getInstance();
        List<com.arquitectura.mensajeria.payload.PayloadClienteRemoto> clientes =
                payload.getClientes() != null ? payload.getClientes() : java.util.Collections.emptyList();

        gestorPeers.actualizarCacheClientes(servidorOrigen, clientes);

        LOGGER.fine(() -> "Cache de clientes actualizado desde " + servidorOrigen
                + " | clientes=" + cantidad);

        Mensaje<String> mensajeRespuesta = new Mensaje<>();
        mensajeRespuesta.setTipo(TipoMensaje.RESPONSE);
        mensajeRespuesta.setAccion(Accion.REPLICAR_CLIENTES);
        mensajeRespuesta.setMetadata(crearMetadata());
        mensajeRespuesta.setPayload("Cache actualizado para " + servidorOrigen);

        Respuesta<String> respuesta = new Respuesta<>();
        respuesta.setEstado(Estado.EXITO);
        respuesta.setMensaje(mensajeRespuesta);
        return respuesta;
    }

    @Override
    public Class<PayloadReplicarClientes> getPayloadClass() {
        return PayloadReplicarClientes.class;
    }

    private Metadata crearMetadata() {
        Metadata metadata = new Metadata();
        metadata.setIdMensaje(UUID.randomUUID().toString());
        metadata.setTimestamp(LocalDateTime.now());
        return metadata;
    }
}
