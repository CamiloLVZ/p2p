package com.arquitectura.dominio.handlers;

import com.arquitectura.aplicacion.router.Handler;
import com.arquitectura.dominio.modelo.ArchivoRecibidoModel;
import com.arquitectura.dominio.repositorios.ArchivoRecibidoRepository;
import com.arquitectura.dominio.repositorios.JpaArchivoRecibidoRepository;
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

public class ListarDocumentosHandler implements Handler<Object> {

    private static final Logger LOGGER = Logger.getLogger(ListarDocumentosHandler.class.getName());
    private final ArchivoRecibidoRepository archivoRepository = new JpaArchivoRecibidoRepository();

    @Override
    public Respuesta<?> handle(Mensaje<Object> mensaje) {
        try {
            // Obtener el username del cliente que solicita (desde metadata.clientId)
            String username = null;
            if (mensaje.getMetadata() != null && mensaje.getMetadata().getClientId() != null
                    && !mensaje.getMetadata().getClientId().isBlank()) {
                username = mensaje.getMetadata().getClientId();
            }

            // Si tenemos username: filtrar por destinatario (broadcast + unicast propio)
            // Si no: fallback a listarTodos (compatibilidad)
            List<ArchivoRecibidoModel> archivos = (username != null)
                    ? archivoRepository.listarParaUsuario(username)
                    : archivoRepository.listarTodos();

            List<Map<String, Object>> resultado = new ArrayList<>();
            for (ArchivoRecibidoModel a : archivos) {
                Map<String, Object> map = new HashMap<>();
                map.put("id", a.getId());
                map.put("name", a.getNombreArchivo());
                map.put("size", a.getTamano());
                map.put("type", a.getExtension());
                map.put("date", a.getFechaRecepcion() != null ? a.getFechaRecepcion().toString() : "");
                map.put("hashSha256", a.getHashSha256());
                map.put("ipRemitente", a.getIpRemitente());
                map.put("origen", esLocal(a.getIpRemitente()) ? "LOCAL" : "EXTERNO");
                resultado.add(map);
            }

            LOGGER.info(() -> "Listando documentos: %d registros".formatted(resultado.size()));

            Mensaje<List<Map<String, Object>>> mensajeRespuesta = new Mensaje<>();
            mensajeRespuesta.setTipo(TipoMensaje.RESPONSE);
            mensajeRespuesta.setAccion(Accion.LISTAR_DOCUMENTOS);
            mensajeRespuesta.setMetadata(crearMetadata());
            mensajeRespuesta.setPayload(resultado);

            Respuesta<List<Map<String, Object>>> respuesta = new Respuesta<>();
            respuesta.setEstado(Estado.EXITO);
            respuesta.setMensaje(mensajeRespuesta);

            return respuesta;

        } catch (Exception e) {
            LOGGER.severe(() -> "Error al listar documentos: " + e.getMessage());

            Mensaje<String> mensajeError = new Mensaje<>();
            mensajeError.setTipo(TipoMensaje.RESPONSE);
            mensajeError.setAccion(Accion.LISTAR_DOCUMENTOS);
            mensajeError.setMetadata(crearMetadata());
            mensajeError.setPayload("Error al obtener los documentos: " + e.getMessage());

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
