package com.arquitectura.aplicacion.router;

import com.arquitectura.infraestructura.serializacion.JsonUtil;
import com.arquitectura.mensajeria.ErrorDetalle;
import com.arquitectura.mensajeria.Mensaje;
import com.arquitectura.mensajeria.Respuesta;
import com.arquitectura.mensajeria.enums.Accion;
import com.arquitectura.mensajeria.enums.Estado;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MensajeRouter {

    private static final Logger LOGGER = Logger.getLogger(MensajeRouter.class.getName());

    private final Map<Accion, Handler<?>> handlers = new HashMap<>();

    public void registrarHandler(Accion accion, Handler<?> handler) {
        handlers.put(accion, handler);
    }

    public Respuesta<?> responder(Mensaje<?> mensaje) {

        Handler<?> handler = handlers.get(mensaje.getAccion());

        if (handler == null) {
            return crearError("ACCION_NO_SOPORTADA", "No existe handler para la accion");
        }

        try {
            return ejecutarHandler(handler, mensaje);
        } catch (Exception e) {
            return crearError("ERROR_INTERNO", e.getMessage());
        }
    }

    private <T> Respuesta<?> ejecutarHandler(Handler<T> handler, Mensaje<?> mensaje) {

        T payloadConvertido = JsonUtil.convert(mensaje.getPayload(), handler.getPayloadClass());

        Mensaje<T> mensajeTipado = new Mensaje<>();
        mensajeTipado.setTipo(mensaje.getTipo());
        mensajeTipado.setAccion(mensaje.getAccion());
        mensajeTipado.setMetadata(mensaje.getMetadata());
        mensajeTipado.setPayload(payloadConvertido);

        return handler.handle(mensajeTipado);
    }

    private Respuesta<?> crearError(String codigo, String mensajeError) {

        ErrorDetalle error = new ErrorDetalle(codigo, mensajeError);

        Respuesta<?> respuesta = new Respuesta<>();
        respuesta.setEstado(Estado.ERROR);
        respuesta.setError(error);

        return respuesta;
    }
}
