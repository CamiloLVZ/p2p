package com.cliente.application.service;

import com.arquitectura.mensajeria.Mensaje;
import com.arquitectura.mensajeria.Respuesta;
import com.arquitectura.mensajeria.enums.Accion;
import com.arquitectura.mensajeria.enums.Estado;
import com.arquitectura.mensajeria.enums.Protocolo;
import com.cliente.infrastructure.protocol.ServerJsonUtil;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Service for federation topology queries.
 *
 * <ul>
 *   <li>{@link #listarServidores()} — list known peer servers (LISTAR_SERVIDORES)</li>
 *   <li>{@link #obtenerEstado()} — get status of the connected server (ESTADO_SERVIDOR)</li>
 * </ul>
 */
public class ServerService {

    private static ServerService instance;

    private ServerService() {}

    public static ServerService getInstance() {
        if (instance == null) instance = new ServerService();
        return instance;
    }

    /**
     * Return the list of servers known to the local server.
     * Each entry is a raw Map (fields: servidorId, host, puerto, estado, ultimaConexion).
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> listarServidores() throws Exception {
        Protocolo proto = resolveProtocolo();
        Mensaje<?> msg = ServerJsonUtil.buildRequest(
                Accion.LISTAR_SERVIDORES, null,
                ConnectionService.getInstance().getClientId(), proto);

        @SuppressWarnings("unchecked")
        Respuesta<?> resp = ConnectionService.getInstance().send((Mensaje<Object>) msg);

        if (resp.getEstado() == Estado.ERROR
                || resp.getMensaje() == null
                || resp.getMensaje().getPayload() == null) {
            return List.of();
        }

        Object raw = resp.getMensaje().getPayload();
        if (raw instanceof List<?> list) {
            List<Map<String, Object>> result = new ArrayList<>();
            ObjectMapper mapper = new ObjectMapper();
            for (Object item : list) {
                result.add(mapper.convertValue(item, Map.class));
            }
            return result;
        }
        return List.of();
    }

    /**
     * Return a snapshot of the server's current status.
     * Fields: servidorId, uptime, totalClientes, totalPeers, peersConectados, etc.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> obtenerEstado() throws Exception {
        Protocolo proto = resolveProtocolo();
        Mensaje<?> msg = ServerJsonUtil.buildRequest(
                Accion.ESTADO_SERVIDOR, null,
                ConnectionService.getInstance().getClientId(), proto);

        @SuppressWarnings("unchecked")
        Respuesta<?> resp = ConnectionService.getInstance().send((Mensaje<Object>) msg);

        if (resp.getEstado() == Estado.ERROR
                || resp.getMensaje() == null
                || resp.getMensaje().getPayload() == null) {
            return Map.of();
        }

        Object raw = resp.getMensaje().getPayload();
        return new ObjectMapper().convertValue(raw, Map.class);
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private Protocolo resolveProtocolo() {
        return ConnectionService.getInstance().getProtocol() == com.cliente.domain.enums.Protocol.TCP
                ? Protocolo.TCP : Protocolo.UDP;
    }
}
