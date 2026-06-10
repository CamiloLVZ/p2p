package com.cliente.application.service;

import com.arquitectura.mensajeria.Mensaje;
import com.arquitectura.mensajeria.Respuesta;
import com.arquitectura.mensajeria.enums.Accion;
import com.arquitectura.mensajeria.enums.Estado;
import com.arquitectura.mensajeria.enums.Protocolo;
import com.arquitectura.mensajeria.payload.PayloadEnviarMensaje;
import com.cliente.domain.model.Message;
import com.cliente.infrastructure.persistence.LocalDocumentRepository;
import com.cliente.infrastructure.protocol.ServerJsonUtil;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MessageService {

    private static final Logger LOG = Logger.getLogger(MessageService.class.getName());
    private static MessageService instance;

    private MessageService() {}

    public static MessageService getInstance() {
        if (instance == null) instance = new MessageService();
        return instance;
    }

    public List<Message> getMessages() throws Exception {
        Protocolo proto = resolveProtocolo();
        Mensaje<?> msg = ServerJsonUtil.buildRequest(
                Accion.LISTAR_MENSAJES, null,
                ConnectionService.getInstance().getClientId(), proto);

        @SuppressWarnings("unchecked")
        Respuesta<?> resp = ConnectionService.getInstance().send((Mensaje<Object>) msg);

        if (resp.getEstado() == Estado.ERROR
                || resp.getMensaje() == null
                || resp.getMensaje().getPayload() == null) {
            return List.of();
        }
        return ServerJsonUtil.convertList(resp.getMensaje().getPayload(), Message.class);
    }

    /** Broadcast shortcut — no destinatario. */
    public void sendMessage(String content) throws Exception {
        sendMessage(content, null);
    }

    /**
     * Send a text message.
     *
     * @param content      message body (never null/blank)
     * @param destinatario target username for unicast, or null for broadcast
     */
    public void sendMessage(String content, String destinatario) throws Exception {
        String username = ConnectionService.getInstance().getUsername();
        Protocolo proto = resolveProtocolo();

        PayloadEnviarMensaje payload = new PayloadEnviarMensaje(username, content);
        if (destinatario != null && !destinatario.isBlank()) {
            payload.setDestinatario(destinatario.trim());
        }

        Mensaje<PayloadEnviarMensaje> msg = ServerJsonUtil.buildRequest(
                Accion.ENVIAR_MENSAJE, payload, username, proto);

        ConnectionService.getInstance().send(msg);

        // Persistir localmente en H2 de forma asíncrona — no bloquea el hilo de JavaFX
        String msgId    = msg.getMetadata().getIdMensaje();
        String host     = ConnectionService.getInstance().getHost();
        int    puerto   = ConnectionService.getInstance().getPort();

        CompletableFuture.runAsync(() -> {
            try {
                new LocalDocumentRepository().guardarMensajeEnviado(
                        msgId,
                        username,
                        null,         // ip_remitente: no disponible en el cliente
                        content,
                        null,         // hash_sha256: no calculado para mensajes de texto
                        null,         // contenido_cifrado: no disponible en el cliente
                        host,
                        puerto
                );
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Error persistiendo mensaje en H2 (no-bloqueante): " + e.getMessage(), e);
            }
        });
    }

    private Protocolo resolveProtocolo() {
        return ConnectionService.getInstance().getProtocol() == com.cliente.domain.enums.Protocol.TCP
                ? Protocolo.TCP : Protocolo.UDP;
    }
}
