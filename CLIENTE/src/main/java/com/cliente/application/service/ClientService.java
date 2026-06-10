package com.cliente.application.service;

import com.arquitectura.mensajeria.Mensaje;
import com.arquitectura.mensajeria.Respuesta;
import com.arquitectura.mensajeria.enums.Accion;
import com.arquitectura.mensajeria.enums.Estado;
import com.arquitectura.mensajeria.enums.Protocolo;
import com.cliente.domain.model.Client;
import com.cliente.infrastructure.protocol.ServerJsonUtil;

import java.util.List;

public class ClientService {

    private static ClientService instance;

    private ClientService() {}

    public static ClientService getInstance() {
        if (instance == null) instance = new ClientService();
        return instance;
    }

    public List<Client> getConnectedClients() throws Exception {
        Protocolo proto = resolveProtocolo();
        Mensaje<?> msg = ServerJsonUtil.buildRequest(
                Accion.LISTAR_CLIENTES, null,
                ConnectionService.getInstance().getClientId(), proto);

        @SuppressWarnings("unchecked")
        Respuesta<?> resp = ConnectionService.getInstance().send((Mensaje<Object>) msg);

        if (resp.getEstado() == Estado.ERROR
                || resp.getMensaje() == null
                || resp.getMensaje().getPayload() == null) {
            return List.of();
        }
        return ServerJsonUtil.convertList(resp.getMensaje().getPayload(), Client.class);
    }

    private Protocolo resolveProtocolo() {
        return ConnectionService.getInstance().getProtocol() == com.cliente.domain.enums.Protocol.TCP
                ? Protocolo.TCP : Protocolo.UDP;
    }
}
