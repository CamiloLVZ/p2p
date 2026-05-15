package com.arquitectura.dominio.handlers;

import com.arquitectura.aplicacion.ContextoSolicitud;
import com.arquitectura.aplicacion.router.Handler;
import com.arquitectura.aplicacion.sesion.GestorServidoresPeer;
import com.arquitectura.aplicacion.sesion.GestorSesiones;
import com.arquitectura.aplicacion.sesion.ResultadoRegistroSesion;
import com.arquitectura.aplicacion.sesion.SesionCliente;
import com.arquitectura.mensajeria.ErrorDetalle;
import com.arquitectura.mensajeria.Mensaje;
import com.arquitectura.mensajeria.Metadata;
import com.arquitectura.mensajeria.Respuesta;
import com.arquitectura.mensajeria.enums.Accion;
import com.arquitectura.mensajeria.enums.Estado;
import com.arquitectura.mensajeria.enums.TipoMensaje;
import com.arquitectura.mensajeria.payload.PayloadClienteRemoto;
import com.arquitectura.mensajeria.payload.PayloadConectar;
import com.arquitectura.mensajeria.payload.PayloadReplicarClientes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

public class ConectarHandler implements Handler<PayloadConectar> {

    private static final Logger LOGGER = Logger.getLogger(ConectarHandler.class.getName());
    private final GestorSesiones gestorSesiones = GestorSesiones.getInstance();

    @Override
    public Respuesta<?> handle(Mensaje<PayloadConectar> mensaje) {
        PayloadConectar payload = mensaje.getPayload();
        String username = payload != null ? payload.getUsername() : null;
        String endpoint = ContextoSolicitud.obtenerEndpointRemitente();
        String protocolo = ContextoSolicitud.obtenerProtocolo();

        ResultadoRegistroSesion registro = gestorSesiones.registrar(username, endpoint, protocolo);
        if (!registro.exito()) {
            LOGGER.warning(() -> "Registro rechazado para [" + username + "] desde " + endpoint
                    + ". Motivo: " + registro.mensaje());
            return crearError(registro.codigoError(), registro.mensaje());
        }

        LOGGER.info(() -> "Usuario conectado: " + registro.sesion().getUsername()
                + " | endpoint=" + registro.sesion().getEndpoint()
                + " | protocolo=" + registro.sesion().getProtocolo()
                + " | sesionesActivas=" + gestorSesiones.sesionesActivas());

        Mensaje<String> mensajeRespuesta = new Mensaje<>();
        mensajeRespuesta.setTipo(TipoMensaje.RESPONSE);
        mensajeRespuesta.setAccion(Accion.CONECTAR);

        Metadata metadata = new Metadata();
        metadata.setIdMensaje(UUID.randomUUID().toString());
        metadata.setTimestamp(LocalDateTime.now());
        metadata.setClientId(registro.sesion().getUsername());
        mensajeRespuesta.setMetadata(metadata);
        mensajeRespuesta.setPayload(registro.mensaje() + ": " + registro.sesion().getUsername());

        Respuesta<String> respuesta = new Respuesta<>();
        respuesta.setMensaje(mensajeRespuesta);
        respuesta.setEstado(Estado.EXITO);

        LOGGER.info(() -> "Respuesta de conexion generada para " + registro.sesion().getUsername()
                + (registro.reconexion() ? " (reconexion)" : ""));

        // Notificar a los peers de la lista actualizada de clientes locales (async)
        empujarClientesAPeers();

        return respuesta;
    }

    @Override
    public Class<PayloadConectar> getPayloadClass() {
        return PayloadConectar.class;
    }

    private Respuesta<?> crearError(String codigo, String detalle) {
        Respuesta<?> respuesta = new Respuesta<>();
        respuesta.setEstado(Estado.ERROR);
        respuesta.setError(new ErrorDetalle(codigo, detalle));
        return respuesta;
    }

    /**
     * Construye un PayloadReplicarClientes con todas las sesiones locales actuales
     * y lo envia a todos los peers de forma asincrona.
     */
    private void empujarClientesAPeers() {
        GestorServidoresPeer gestorPeers = GestorServidoresPeer.getInstance();
        String servidorId = gestorPeers.getServidorId();
        Collection<SesionCliente> sesiones = gestorSesiones.listarSesiones();

        List<PayloadClienteRemoto> clientes = new ArrayList<>();
        for (SesionCliente s : sesiones) {
            clientes.add(new PayloadClienteRemoto(
                    s.getUsername(), s.getIpRemitente(), s.getPuertoRemitente(), s.getProtocolo(), servidorId));
        }

        PayloadReplicarClientes payload = new PayloadReplicarClientes();
        payload.setServidorOrigen(servidorId);
        payload.setClientes(clientes);

        Mensaje<PayloadReplicarClientes> msg = new Mensaje<>();
        msg.setTipo(TipoMensaje.REQUEST);
        msg.setAccion(Accion.REPLICAR_CLIENTES);
        Metadata meta = new Metadata();
        meta.setIdMensaje(UUID.randomUUID().toString());
        meta.setTimestamp(LocalDateTime.now());
        msg.setMetadata(meta);
        msg.setPayload(payload);

        gestorPeers.enviarATodos(msg);
        LOGGER.fine(() -> "Push de clientes a peers: " + clientes.size() + " clientes locales");
    }
}
