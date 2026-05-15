package com.arquitectura.dominio.handlers;

import com.arquitectura.aplicacion.router.Handler;
import com.arquitectura.dominio.modelo.LogServidorModel;
import com.arquitectura.dominio.repositorios.JpaLogServidorRepository;
import com.arquitectura.dominio.repositorios.LogServidorRepository;
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

public class ListarLogsHandler implements Handler<Object> {

    private static final Logger LOGGER = Logger.getLogger(ListarLogsHandler.class.getName());
    private final LogServidorRepository logRepository = new JpaLogServidorRepository();

    @Override
    public Respuesta<?> handle(Mensaje<Object> mensaje) {
        try {
            int pagina = 0;
            int tamanoPagina = 50;

            if (mensaje.getPayload() instanceof Map<?,?> payloadMap) {
                Object pag = payloadMap.get("pagina");
                Object tam = payloadMap.get("tamanoPagina");
                if (pag != null) pagina = ((Number) pag).intValue();
                if (tam != null) tamanoPagina = Math.min(((Number) tam).intValue(), 200);
            }

            List<LogServidorModel> logs = logRepository.listarPaginado(pagina, tamanoPagina);
            long totalRegistros = logRepository.contarTotal();

            List<Map<String, Object>> resultado = new ArrayList<>();
            for (LogServidorModel l : logs) {
                Map<String, Object> map = new HashMap<>();
                String fechaEvento = l.getFechaEvento() != null ? l.getFechaEvento().toString() : "";
                // Separar fecha y hora para que coincidan con los campos de LogEntry (date, time)
                String date = fechaEvento.length() >= 10 ? fechaEvento.substring(0, 10) : fechaEvento;
                String time = fechaEvento.length() > 10  ? fechaEvento.substring(11, Math.min(19, fechaEvento.length())) : "";
                String description = "[" + l.getNivel() + "] "
                        + (l.getOrigen() != null ? l.getOrigen() + " - " : "")
                        + l.getMensaje();
                map.put("date", date);
                map.put("time", time);
                map.put("description", description);
                resultado.add(map);
            }

            final int paginaFinal = pagina;
            final int tamanoPaginaFinal = tamanoPagina;
            LOGGER.info(() -> "Listando logs: página %d, %d registros de %d totales".formatted(paginaFinal, resultado.size(), totalRegistros));

            Map<String, Object> paginatedPayload = new HashMap<>();
            paginatedPayload.put("registros", resultado);
            paginatedPayload.put("pagina", paginaFinal);
            paginatedPayload.put("tamanoPagina", tamanoPaginaFinal);
            paginatedPayload.put("totalRegistros", totalRegistros);
            paginatedPayload.put("totalPaginas", (int) Math.ceil((double) totalRegistros / tamanoPagina));

            Mensaje<Map<String, Object>> mensajeRespuesta = new Mensaje<>();
            mensajeRespuesta.setTipo(TipoMensaje.RESPONSE);
            mensajeRespuesta.setAccion(Accion.LISTAR_LOGS);
            mensajeRespuesta.setMetadata(crearMetadata());
            mensajeRespuesta.setPayload(paginatedPayload);

            Respuesta<Map<String, Object>> respuesta = new Respuesta<>();
            respuesta.setEstado(Estado.EXITO);
            respuesta.setMensaje(mensajeRespuesta);

            return respuesta;

        } catch (Exception e) {
            LOGGER.severe(() -> "Error al listar logs: " + e.getMessage());

            Mensaje<String> mensajeError = new Mensaje<>();
            mensajeError.setTipo(TipoMensaje.RESPONSE);
            mensajeError.setAccion(Accion.LISTAR_LOGS);
            mensajeError.setMetadata(crearMetadata());
            mensajeError.setPayload("Error al obtener los logs: " + e.getMessage());

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

    private Metadata crearMetadata() {
        Metadata metadata = new Metadata();
        metadata.setIdMensaje(UUID.randomUUID().toString());
        metadata.setTimestamp(LocalDateTime.now());
        return metadata;
    }
}
