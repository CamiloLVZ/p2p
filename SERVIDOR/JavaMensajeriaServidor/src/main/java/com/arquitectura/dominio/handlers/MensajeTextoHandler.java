package com.arquitectura.dominio.handlers;

import com.arquitectura.aplicacion.ContextoSolicitud;
import com.arquitectura.aplicacion.router.Handler;
import com.arquitectura.aplicacion.sesion.GestorSesiones;
import com.arquitectura.aplicacion.sesion.GestorServidoresPeer;
import com.arquitectura.aplicacion.sesion.ResultadoValidacionSesion;
import com.arquitectura.dominio.repositorios.JpaMensajeRepository;
import com.arquitectura.dominio.repositorios.MensajeRepository;
import com.arquitectura.infraestructura.seguridad.CryptoUtil;
import com.arquitectura.mensajeria.ErrorDetalle;
import com.arquitectura.mensajeria.Mensaje;
import com.arquitectura.mensajeria.Metadata;
import com.arquitectura.mensajeria.Respuesta;
import com.arquitectura.mensajeria.enums.Accion;
import com.arquitectura.mensajeria.enums.Estado;
import com.arquitectura.mensajeria.enums.TipoMensaje;
import com.arquitectura.mensajeria.payload.PayloadClienteRemoto;
import com.arquitectura.mensajeria.payload.PayloadEntregarMensaje;
import com.arquitectura.mensajeria.payload.PayloadEnviarMensaje;
import com.arquitectura.mensajeria.payload.PayloadReplicarMensaje;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

public class MensajeTextoHandler implements Handler<PayloadEnviarMensaje> {

    private static final Logger LOGGER = Logger.getLogger(MensajeTextoHandler.class.getName());
    private final MensajeRepository mensajeRepository = new JpaMensajeRepository();
    private final GestorSesiones gestorSesiones = GestorSesiones.getInstance();

    @Override
    public Respuesta<?> handle(Mensaje<PayloadEnviarMensaje> mensaje) {
        PayloadEnviarMensaje payload = mensaje.getPayload();
        String remitente = resolverRemitente(mensaje, payload);
        ResultadoValidacionSesion validacion = gestorSesiones.validarSesion(
                remitente,
                ContextoSolicitud.obtenerIpRemitente(),
                puertoRemitente(),
                ContextoSolicitud.obtenerProtocolo()
        );
        if (!validacion.exito()) {
            return crearErrorSesion(validacion.codigoError(), validacion.mensaje());
        }

        String ipRemitente = ContextoSolicitud.obtenerIpRemitente();
        String texto = payload.getContenido();
        String destinatario = payload.getDestinatario();
        LocalDateTime fechaEnvio = resolverFecha(mensaje);
        String mensajeId = resolverMensajeId(mensaje);
        byte[] contenidoBytes = texto.getBytes(StandardCharsets.UTF_8);
        String hashSha256 = CryptoUtil.sha256Base64(contenidoBytes);
        String contenidoCifrado = CryptoUtil.aesEncryptBase64(contenidoBytes);

        mensajeRepository.guardar(mensajeId, remitente, ipRemitente, texto, hashSha256, contenidoCifrado, fechaEnvio, null, destinatario);

        LOGGER.info(() -> "Mensaje de texto recibido | Remitente: %s | IP: %s | Hash: %s | Destinatario: %s"
                .formatted(remitente, ipRemitente, hashSha256, destinatario != null ? destinatario : "broadcast"));

        System.out.println("[SERVIDOR] Mensaje recibido de " + remitente + " (" + ipRemitente + "): " + texto);

        // Replication — fire-and-forget
        replicarMensaje(mensajeId, remitente, texto, fechaEnvio, destinatario);

        Mensaje<String> mensajeRespuesta = new Mensaje<>();
        mensajeRespuesta.setTipo(TipoMensaje.RESPONSE);
        mensajeRespuesta.setAccion(Accion.ENVIAR_MENSAJE);
        mensajeRespuesta.setMetadata(crearMetadataRespuesta());
        mensajeRespuesta.setPayload("Mensaje recibido correctamente");

        Respuesta<String> respuesta = new Respuesta<>();
        respuesta.setEstado(Estado.EXITO);
        respuesta.setMensaje(mensajeRespuesta);
        return respuesta;
    }

    @Override
    public Class<PayloadEnviarMensaje> getPayloadClass() {
        return PayloadEnviarMensaje.class;
    }

    // -------------------------------------------------------------------------
    // Replication helpers
    // -------------------------------------------------------------------------

    private void replicarMensaje(String mensajeId, String autor, String contenido,
                                  LocalDateTime timestamp, String destinatario) {
        GestorServidoresPeer peers = GestorServidoresPeer.getInstance();

        List<com.arquitectura.aplicacion.sesion.ConexionPeer> todosLosPeers = peers.obtenerPeers();
        List<com.arquitectura.aplicacion.sesion.ConexionPeer> peersConectados = peers.obtenerPeersConectados();

        LOGGER.fine(() -> "Peers en mapa: " + todosLosPeers.size()
                + " | Peers conectados: " + peersConectados.size()
                + " | Detalle: " + todosLosPeers.stream()
                    .map(p -> p.getConfig().getServidorId() + "=" + p.getEstado())
                    .collect(java.util.stream.Collectors.joining(", ")));

        if (destinatario == null || destinatario.isBlank()) {
            // Broadcast
            LOGGER.fine(() -> "Broadcast de '" + autor + "': enviando a todos los peers");
            PayloadReplicarMensaje replicaPayload = new PayloadReplicarMensaje();
            replicaPayload.setId(mensajeId);
            replicaPayload.setAutor(autor);
            replicaPayload.setContenido(contenido);
            replicaPayload.setServidorOrigen(peers.getServidorId());
            replicaPayload.setDestinatario(null);
            replicaPayload.setTimestamp(timestamp);

            Mensaje<PayloadReplicarMensaje> msgReplica = buildS2SMensaje(Accion.REPLICAR_MENSAJE, replicaPayload);
            peers.enviarATodos(msgReplica);
            LOGGER.fine(() -> "Broadcast disparado para: " + mensajeId);
        } else {
            // Unicast
            LOGGER.fine(() -> "Unicast de '" + autor + "' para destinatario: '" + destinatario + "'");
            if (gestorSesiones.existeSesionActiva(destinatario)) {
                LOGGER.fine(() -> "Destinatario '" + destinatario + "' es LOCAL — quedara en DB para poll");
            } else {
                List<PayloadClienteRemoto> remotos = peers.obtenerTodosClientesRemotos();
                LOGGER.fine(() -> "Cache de clientes remotos: " + remotos.size()
                        + " | " + remotos.stream()
                            .map(r -> r.getUsername() + "@" + r.getServidorOrigen())
                            .collect(java.util.stream.Collectors.joining(", ")));

                String peerOwner = null;
                for (PayloadClienteRemoto remoto : remotos) {
                    if (destinatario.equals(remoto.getUsername())) {
                        peerOwner = remoto.getServidorOrigen();
                        break;
                    }
                }

                if (peerOwner != null) {
                    final String peerOwnerFinal = peerOwner;
                    LOGGER.fine(() -> "Destinatario '" + destinatario + "' encontrado en peer: " + peerOwnerFinal);
                    PayloadEntregarMensaje entregarPayload = new PayloadEntregarMensaje();
                    entregarPayload.setDestinatario(destinatario);
                    entregarPayload.setAutor(autor);
                    entregarPayload.setContenido(contenido);
                    entregarPayload.setServidorOrigen(peers.getServidorId());
                    entregarPayload.setTimestamp(timestamp);

                    Mensaje<PayloadEntregarMensaje> msgEntregar = buildS2SMensaje(Accion.ENTREGAR_MENSAJE, entregarPayload);
                    boolean ok = peers.enviarAPeer(peerOwnerFinal, msgEntregar);
                    LOGGER.fine(() -> "enviarAPeer(" + peerOwnerFinal + ") resultado: " + (ok ? "OK" : "FALLO"));
                    if (!ok) {
                        LOGGER.warning("Envio directo fallido a " + peerOwnerFinal + ", intentando via otros peers");
                        for (com.arquitectura.aplicacion.sesion.ConexionPeer peer : peers.obtenerPeersConectados()) {
                            if (!peer.getConfig().getServidorId().equals(peerOwnerFinal)) {
                                boolean okFallback = peers.enviarAPeer(peer.getConfig().getServidorId(), msgEntregar);
                                LOGGER.fine(() -> "Fallback via " + peer.getConfig().getServidorId() + ": " + (okFallback ? "OK" : "FALLO"));
                            }
                        }
                    }
                } else {
                    LOGGER.warning("Destinatario '" + destinatario + "' NO encontrado ni local ni en cache remoto");
                }
            }
        }
    }

    private <T> Mensaje<T> buildS2SMensaje(Accion accion, T payload) {
        Metadata meta = new Metadata();
        meta.setIdMensaje(UUID.randomUUID().toString());
        meta.setTimestamp(LocalDateTime.now());

        Mensaje<T> msg = new Mensaje<>();
        msg.setTipo(TipoMensaje.REQUEST);
        msg.setAccion(accion);
        msg.setMetadata(meta);
        msg.setPayload(payload);
        return msg;
    }

    private Metadata crearMetadataRespuesta() {
        Metadata metadata = new Metadata();
        metadata.setIdMensaje(UUID.randomUUID().toString());
        metadata.setTimestamp(LocalDateTime.now());
        return metadata;
    }

    private String resolverRemitente(Mensaje<PayloadEnviarMensaje> mensaje, PayloadEnviarMensaje payload) {
        if (mensaje.getMetadata() != null && mensaje.getMetadata().getClientId() != null) {
            return mensaje.getMetadata().getClientId();
        }
        if (payload != null && payload.getAutor() != null && !payload.getAutor().isBlank()) {
            return payload.getAutor();
        }
        return "desconocido";
    }

    private LocalDateTime resolverFecha(Mensaje<PayloadEnviarMensaje> mensaje) {
        if (mensaje.getMetadata() != null && mensaje.getMetadata().getTimestamp() != null) {
            return mensaje.getMetadata().getTimestamp();
        }
        return LocalDateTime.now();
    }

    private String resolverMensajeId(Mensaje<PayloadEnviarMensaje> mensaje) {
        if (mensaje.getMetadata() != null && mensaje.getMetadata().getIdMensaje() != null) {
            String idMensaje = mensaje.getMetadata().getIdMensaje();
            try {
                return UUID.fromString(idMensaje).toString();
            } catch (IllegalArgumentException ignored) {
                LOGGER.warning(() -> "idMensaje recibido no es UUID valido, se generara uno nuevo");
            }
        }
        return UUID.randomUUID().toString();
    }

    private int puertoRemitente() {
        Integer puerto = ContextoSolicitud.obtenerPuertoRemitente();
        return puerto == null ? -1 : puerto;
    }

    private Respuesta<?> crearErrorSesion(String codigo, String detalle) {
        Respuesta<?> respuesta = new Respuesta<>();
        respuesta.setEstado(Estado.ERROR);
        respuesta.setError(new ErrorDetalle(codigo, detalle));
        return respuesta;
    }
}
