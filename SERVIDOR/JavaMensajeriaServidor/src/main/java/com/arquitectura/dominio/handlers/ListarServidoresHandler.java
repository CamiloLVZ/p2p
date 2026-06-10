package com.arquitectura.dominio.handlers;

import com.arquitectura.aplicacion.router.Handler;
import com.arquitectura.aplicacion.sesion.ConexionPeer;
import com.arquitectura.aplicacion.sesion.GestorServidoresPeer;
import com.arquitectura.mensajeria.Mensaje;
import com.arquitectura.mensajeria.Metadata;
import com.arquitectura.mensajeria.Respuesta;
import com.arquitectura.mensajeria.enums.Accion;
import com.arquitectura.mensajeria.enums.Estado;
import com.arquitectura.mensajeria.enums.TipoMensaje;
import com.arquitectura.mensajeria.payload.PayloadServidorInfo;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

public class ListarServidoresHandler implements Handler<Object> {

    private static final Logger LOGGER = Logger.getLogger(ListarServidoresHandler.class.getName());

    @Override
    public Respuesta<?> handle(Mensaje<Object> mensaje) {
        List<ConexionPeer> peers = GestorServidoresPeer.getInstance().obtenerPeers();
        List<PayloadServidorInfo> resultado = new ArrayList<>();

        for (ConexionPeer peer : peers) {
            PayloadServidorInfo info = new PayloadServidorInfo();
            info.setServidorId(peer.getConfig().getServidorId());
            info.setHost(peer.getConfig().getHost());
            info.setPuerto(peer.getConfig().getPuerto());
            info.setEstado(peer.estaConectado() ? "CONECTADO" : "DESCONECTADO");
            info.setUltimaConexion(peer.getUltimaConexion());
            resultado.add(info);
        }

        LOGGER.info(() -> "Listando servidores: " + resultado.size() + " peers conocidos");

        Mensaje<List<PayloadServidorInfo>> mensajeRespuesta = new Mensaje<>();
        mensajeRespuesta.setTipo(TipoMensaje.RESPONSE);
        mensajeRespuesta.setAccion(Accion.LISTAR_SERVIDORES);
        mensajeRespuesta.setMetadata(crearMetadata());
        mensajeRespuesta.setPayload(resultado);

        Respuesta<List<PayloadServidorInfo>> respuesta = new Respuesta<>();
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
