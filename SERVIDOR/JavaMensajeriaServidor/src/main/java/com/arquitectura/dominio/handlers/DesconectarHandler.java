package com.arquitectura.dominio.handlers;

import com.arquitectura.aplicacion.ContextoSolicitud;
import com.arquitectura.aplicacion.router.Handler;
import com.arquitectura.aplicacion.sesion.GestorServidoresPeer;
import com.arquitectura.aplicacion.sesion.GestorSesiones;
import com.arquitectura.aplicacion.sesion.SesionCliente;
import com.arquitectura.mensajeria.ErrorDetalle;
import com.arquitectura.mensajeria.Mensaje;
import com.arquitectura.mensajeria.Metadata;
import com.arquitectura.mensajeria.Respuesta;
import com.arquitectura.mensajeria.enums.Accion;
import com.arquitectura.mensajeria.enums.Estado;
import com.arquitectura.mensajeria.enums.TipoMensaje;
import com.arquitectura.mensajeria.payload.PayloadClienteRemoto;
import com.arquitectura.mensajeria.payload.PayloadReplicarClientes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

public class DesconectarHandler implements Handler<Object> {

    private static final Logger LOGGER = Logger.getLogger(DesconectarHandler.class.getName());
    private final GestorSesiones gestorSesiones = GestorSesiones.getInstance();

    @Override
    public Respuesta<?> handle(Mensaje<Object> mensaje) {
        String username = (mensaje.getMetadata() != null) ? mensaje.getMetadata().getClientId() : null;
        String endpoint = ContextoSolicitud.obtenerEndpointRemitente();
        String protocolo = ContextoSolicitud.obtenerProtocolo();

        if (username == null || username.isBlank()) {
            LOGGER.warning(() -> "Desconexion rechazada: username ausente desde " + endpoint);
            Respuesta<?> respuesta = new Respuesta<>();
            respuesta.setEstado(Estado.ERROR);
            respuesta.setError(new ErrorDetalle("USERNAME_INVALIDO", "El username es obligatorio para desconectarse"));
            return respuesta;
        }

        boolean eliminado = gestorSesiones.eliminar(username);

        if (eliminado) {
            LOGGER.info(() -> "Usuario desconectado: " + username
                    + " | endpoint=" + endpoint
                    + " | protocolo=" + protocolo
                    + " | sesionesActivas=" + gestorSesiones.sesionesActivas());
            empujarClientesAPeers();
        } else {
            LOGGER.warning(() -> "Desconexion de usuario sin sesion activa: " + username
                    + " | endpoint=" + endpoint);
        }

        Respuesta<String> respuesta = new Respuesta<>();
        respuesta.setEstado(Estado.EXITO);
        return respuesta;
    }

    @Override
    public Class<Object> getPayloadClass() {
        return Object.class;
    }

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
        LOGGER.fine(() -> "Push de clientes a peers tras desconexion: " + clientes.size() + " clientes locales");
    }
}
