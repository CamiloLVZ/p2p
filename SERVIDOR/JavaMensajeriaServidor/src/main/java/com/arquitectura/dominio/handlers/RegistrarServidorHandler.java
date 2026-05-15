package com.arquitectura.dominio.handlers;

import com.arquitectura.aplicacion.router.Handler;
import com.arquitectura.aplicacion.sesion.GestorServidoresPeer;
import com.arquitectura.aplicacion.sesion.GestorSesiones;
import com.arquitectura.aplicacion.sesion.SesionCliente;
import com.arquitectura.mensajeria.Mensaje;
import com.arquitectura.mensajeria.Metadata;
import com.arquitectura.mensajeria.Respuesta;
import com.arquitectura.mensajeria.enums.Accion;
import com.arquitectura.mensajeria.enums.Estado;
import com.arquitectura.mensajeria.enums.TipoMensaje;
import com.arquitectura.mensajeria.payload.PayloadClienteRemoto;
import com.arquitectura.mensajeria.payload.PayloadRegistrarServidor;
import com.arquitectura.mensajeria.payload.PayloadReplicarClientes;
import com.arquitectura.aplicacion.sesion.ConexionPeer;
import com.arquitectura.mensajeria.payload.PayloadServidorInfo;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

public class RegistrarServidorHandler implements Handler<PayloadRegistrarServidor> {

    private static final Logger LOGGER = Logger.getLogger(RegistrarServidorHandler.class.getName());

    @Override
    public Respuesta<?> handle(Mensaje<PayloadRegistrarServidor> mensaje) {
        PayloadRegistrarServidor payload = mensaje.getPayload();
        if (payload == null || payload.getServidorId() == null || payload.getServidorId().isBlank()) {
            Respuesta<?> error = new Respuesta<>();
            error.setEstado(Estado.ERROR);
            return error;
        }

        String servidorId = payload.getServidorId();

        // Marcar el peer como conectado — registrar dinámicamente si no estaba en config
        GestorServidoresPeer gestor = GestorServidoresPeer.getInstance();
        gestor.marcarPeerConectado(servidorId, payload.getHost(), payload.getPuerto());

        LOGGER.info(() -> "Peer registrado: " + servidorId
                + " | host=" + payload.getHost()
                + " | puerto=" + payload.getPuerto());

        // Replicar lista de clientes locales al peer que se está registrando
        Collection<SesionCliente> sesionesLocales = GestorSesiones.getInstance().listarSesiones();
        List<PayloadClienteRemoto> clientesLocales = new ArrayList<>();
        for (SesionCliente s : sesionesLocales) {
            PayloadClienteRemoto c = new PayloadClienteRemoto();
            c.setUsername(s.getUsername());
            c.setServidorOrigen(gestor.getServidorId());
            clientesLocales.add(c);
        }

        if (!clientesLocales.isEmpty()) {
            PayloadReplicarClientes payloadReplica = new PayloadReplicarClientes();
            payloadReplica.setServidorOrigen(gestor.getServidorId());
            payloadReplica.setClientes(clientesLocales);

            Mensaje<PayloadReplicarClientes> mensajeReplica = new Mensaje<>();
            mensajeReplica.setTipo(TipoMensaje.REQUEST);
            mensajeReplica.setAccion(Accion.REPLICAR_CLIENTES);
            mensajeReplica.setMetadata(crearMetadata());
            mensajeReplica.setPayload(payloadReplica);

            gestor.enviarAPeer(servidorId, mensajeReplica);
            LOGGER.info(() -> "Lista de clientes locales (" + clientesLocales.size()
                    + ") enviada a peer " + servidorId);
        }

        Mensaje<PayloadRegistrarServidor> mensajeRespuesta = new Mensaje<>();
        mensajeRespuesta.setTipo(TipoMensaje.RESPONSE);
        mensajeRespuesta.setAccion(Accion.REGISTRAR_SERVIDOR);
        mensajeRespuesta.setMetadata(crearMetadata());

        List<PayloadServidorInfo> peersParaCompartir = new ArrayList<>();
        for (ConexionPeer p : gestor.obtenerPeersConectados()) {
            if (p.getConfig().getServidorId().equals(servidorId)) continue;
            PayloadServidorInfo info = new PayloadServidorInfo();
            info.setServidorId(p.getConfig().getServidorId());
            info.setHost(p.getConfig().getHost());
            info.setPuerto(p.getConfig().getPuerto());
            info.setEstado("CONNECTED");
            info.setUltimaConexion(p.getUltimaConexion());
            peersParaCompartir.add(info);
        }

        PayloadRegistrarServidor respPayload = new PayloadRegistrarServidor();
        respPayload.setServidorId(gestor.getServidorId());
        respPayload.setHost(gestor.resolverIpLocalPublic());
        respPayload.setPuerto(gestor.getServidorPuerto());
        respPayload.setVersion("1.0");
        respPayload.setPeersConocidos(peersParaCompartir);
        mensajeRespuesta.setPayload(respPayload);

        Respuesta<PayloadRegistrarServidor> respuesta = new Respuesta<>();
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
