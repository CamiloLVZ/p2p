package com.arquitectura.dominio.handlers;

import com.arquitectura.aplicacion.router.Handler;
import com.arquitectura.dominio.modelo.MensajeModel;
import com.arquitectura.dominio.repositorios.JpaMensajeRepository;
import com.arquitectura.dominio.repositorios.MensajeRepository;
import com.arquitectura.mensajeria.Mensaje;
import com.arquitectura.mensajeria.Metadata;
import com.arquitectura.mensajeria.Respuesta;
import com.arquitectura.mensajeria.enums.Accion;
import com.arquitectura.mensajeria.enums.Estado;
import com.arquitectura.mensajeria.enums.TipoMensaje;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

public class ListarMensajesHandler implements Handler<Object> {

    private static final Logger LOGGER = Logger.getLogger(ListarMensajesHandler.class.getName());
    private final MensajeRepository mensajeRepository = new JpaMensajeRepository();

    @Override
    public Respuesta<?> handle(Mensaje<Object> mensaje) {
        try {
            String username = null;
            if (mensaje.getMetadata() != null) {
                username = mensaje.getMetadata().getClientId();
            }

            List<MensajeModel> mensajes;
            if (username != null && !username.isBlank()) {
                mensajes = mensajeRepository.listarParaUsuario(username);
            } else {
                mensajes = mensajeRepository.listarTodos();
            }

            List<Map<String, Object>> resultado = new ArrayList<>();
            for (MensajeModel m : mensajes) {
                Map<String, Object> map = new HashMap<>();
                map.put("clientId", m.getAutor());
                map.put("content", m.getContenido());
                map.put("timestamp", m.getFechaEnvio() != null ? m.getFechaEnvio().toString() : "");
                map.put("hashSha256", m.getHashSha256());
                map.put("contenidoCifrado", m.getContenidoCifrado());
                map.put("ipRemitente", m.getIpRemitente());
                map.put("origen", esLocal(m.getIpRemitente()) ? "LOCAL" : "EXTERNO");
                resultado.add(map);
            }

            LOGGER.fine(() -> "Listando mensajes: %d registros".formatted(resultado.size()));

            Mensaje<List<Map<String, Object>>> mensajeRespuesta = new Mensaje<>();
            mensajeRespuesta.setTipo(TipoMensaje.RESPONSE);
            mensajeRespuesta.setAccion(Accion.LISTAR_MENSAJES);
            mensajeRespuesta.setMetadata(crearMetadata());
            mensajeRespuesta.setPayload(resultado);

            Respuesta<List<Map<String, Object>>> respuesta = new Respuesta<>();
            respuesta.setEstado(Estado.EXITO);
            respuesta.setMensaje(mensajeRespuesta);

            return respuesta;

        } catch (Exception e) {
            LOGGER.severe(() -> "Error al listar mensajes: " + e.getMessage());

            Mensaje<String> mensajeError = new Mensaje<>();
            mensajeError.setTipo(TipoMensaje.RESPONSE);
            mensajeError.setAccion(Accion.LISTAR_MENSAJES);
            mensajeError.setMetadata(crearMetadata());
            mensajeError.setPayload("Error al obtener los mensajes: " + e.getMessage());

            Respuesta<String> respuestaError = new Respuesta<>();
            respuestaError.setEstado(Estado.ERROR);
            respuestaError.setMensaje(mensajeError);

            return respuestaError;
        }
    }

    @Override
    public Class<Object> getPayloadClass() {
        return Object.class;
    }

    private boolean esLocal(String ip) {
        if (ip == null) return false;
        return "127.0.0.1".equals(ip) || "localhost".equals(ip)
                || "0:0:0:0:0:0:0:1".equals(ip) || "::1".equals(ip);
    }

    private Metadata crearMetadata() {
        Metadata metadata = new Metadata();
        metadata.setIdMensaje(UUID.randomUUID().toString());
        metadata.setTimestamp(LocalDateTime.now());
        return metadata;
    }
}
