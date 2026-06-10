package com.arquitectura.dominio.handlers;

import com.arquitectura.aplicacion.router.Handler;
import com.arquitectura.aplicacion.sesion.GestorServidoresPeer;
import com.arquitectura.aplicacion.sesion.GestorSesiones;
import com.arquitectura.dominio.repositorios.JpaMensajeRepository;
import com.arquitectura.dominio.repositorios.MensajeRepository;
import com.arquitectura.mensajeria.Mensaje;
import com.arquitectura.mensajeria.Metadata;
import com.arquitectura.mensajeria.Respuesta;
import com.arquitectura.mensajeria.enums.Accion;
import com.arquitectura.mensajeria.enums.Estado;
import com.arquitectura.mensajeria.enums.TipoMensaje;
import com.arquitectura.mensajeria.payload.PayloadClienteRemoto;
import com.arquitectura.mensajeria.payload.PayloadEntregarMensaje;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Maneja el mensaje ENTREGAR_MENSAJE — entrega unicast a un destinatario.
 *
 * <p>Flujo:
 * 1. Busca al destinatario en GestorSesiones (local).
 * 2. Si esta local: registra la entrega (arquitectura request-response, no hay push).
 * 3. Si no esta local: busca en que peer esta y reenvía al peer correcto.
 * 4. Si no se encuentra en ningun lado: devuelve exito con aviso (puede haberse desconectado).</p>
 */
public class EntregarMensajeHandler implements Handler<PayloadEntregarMensaje> {

    private static final Logger LOGGER = Logger.getLogger(EntregarMensajeHandler.class.getName());
    private final MensajeRepository mensajeRepository = new JpaMensajeRepository();

    @Override
    public Respuesta<?> handle(Mensaje<PayloadEntregarMensaje> mensaje) {
        PayloadEntregarMensaje payload = mensaje.getPayload();
        if (payload == null || payload.getDestinatario() == null || payload.getDestinatario().isBlank()) {
            Respuesta<?> error = new Respuesta<>();
            error.setEstado(Estado.ERROR);
            return error;
        }

        String destinatario = payload.getDestinatario();
        GestorSesiones gestorSesiones = GestorSesiones.getInstance();
        GestorServidoresPeer gestorPeers = GestorServidoresPeer.getInstance();

        // Paso 1: verificar si el destinatario esta en sesion local
        boolean esLocal = gestorSesiones.existeSesionActiva(destinatario);

        if (esLocal) {
            // El cliente esta en este servidor. Persistir el mensaje unicast para que
            // el destinatario lo reciba en su proximo poll/request.
            LocalDateTime timestamp = payload.getTimestamp() != null ? payload.getTimestamp() : LocalDateTime.now();
            try {
                mensajeRepository.guardar(
                        UUID.randomUUID().toString(),
                        payload.getAutor(),
                        payload.getServidorOrigen(),
                        payload.getContenido(),
                        "",   // hash — no recalcular en entrega peer-to-peer
                        "",   // contenidoCifrado — no necesario para texto plano
                        timestamp,
                        payload.getServidorOrigen(),
                        payload.getDestinatario()
                );
                LOGGER.info(() -> "Mensaje unicast persistido para " + destinatario
                        + " | autor=" + payload.getAutor());
            } catch (Exception e) {
                LOGGER.severe(() -> "Error al persistir mensaje unicast para " + destinatario
                        + ": " + e.getMessage());
            }

            return crearRespuesta("Mensaje entregado localmente a " + destinatario);
        }

        // Paso 2: buscar en cache de clientes remotos
        String peerDestino = encontrarPeerDeCliente(destinatario, gestorPeers);

        if (peerDestino != null) {
            // Reenviar al peer que tiene al cliente
            Mensaje<PayloadEntregarMensaje> mensajeReenvio = new Mensaje<>();
            mensajeReenvio.setTipo(TipoMensaje.REQUEST);
            mensajeReenvio.setAccion(Accion.ENTREGAR_MENSAJE);
            mensajeReenvio.setMetadata(crearMetadata());
            mensajeReenvio.setPayload(payload);

            boolean enviado = gestorPeers.enviarAPeer(peerDestino, mensajeReenvio);

            if (enviado) {
                LOGGER.info(() -> "Mensaje reenviado al peer " + peerDestino
                        + " para entrega a " + destinatario);
                return crearRespuesta("Mensaje reenviado a peer " + peerDestino + " para " + destinatario);
            } else {
                LOGGER.warning(() -> "No se pudo reenviar mensaje al peer " + peerDestino
                        + " para destinatario " + destinatario);
                return crearRespuesta("Fallo reenvio a peer " + peerDestino + " para " + destinatario);
            }
        }

        // Último recurso: broadcast a todos los peers
        Mensaje<PayloadEntregarMensaje> mensajeReenvio = new Mensaje<>();
        mensajeReenvio.setTipo(TipoMensaje.REQUEST);
        mensajeReenvio.setAccion(Accion.ENTREGAR_MENSAJE);
        mensajeReenvio.setMetadata(crearMetadata());
        mensajeReenvio.setPayload(payload);

        LOGGER.warning(() -> "Destinatario " + destinatario + " no encontrado, intentando broadcast a peers");
        for (com.arquitectura.aplicacion.sesion.ConexionPeer peer : gestorPeers.obtenerPeersConectados()) {
            gestorPeers.enviarAPeer(peer.getConfig().getServidorId(), mensajeReenvio);
        }

        // Destinatario no encontrado en ningun lado
        LOGGER.warning(() -> "Destinatario no encontrado localmente ni en peers: " + destinatario);
        return crearRespuesta("Destinatario no encontrado: " + destinatario);
    }

    @Override
    public Class<PayloadEntregarMensaje> getPayloadClass() {
        return PayloadEntregarMensaje.class;
    }

    private String encontrarPeerDeCliente(String username, GestorServidoresPeer gestorPeers) {
        List<PayloadClienteRemoto> remotos = gestorPeers.obtenerTodosClientesRemotos();
        for (PayloadClienteRemoto cliente : remotos) {
            if (username.equalsIgnoreCase(cliente.getUsername())) {
                return cliente.getServidorOrigen();
            }
        }
        return null;
    }

    private Respuesta<String> crearRespuesta(String texto) {
        Mensaje<String> mensajeRespuesta = new Mensaje<>();
        mensajeRespuesta.setTipo(TipoMensaje.RESPONSE);
        mensajeRespuesta.setAccion(Accion.ENTREGAR_MENSAJE);
        mensajeRespuesta.setMetadata(crearMetadata());
        mensajeRespuesta.setPayload(texto);

        Respuesta<String> respuesta = new Respuesta<>();
        respuesta.setEstado(Estado.EXITO);
        respuesta.setMensaje(mensajeRespuesta);
        return respuesta;
    }

    private Metadata crearMetadata() {
        Metadata metadata = new Metadata();
        metadata.setIdMensaje(UUID.randomUUID().toString());
        metadata.setTimestamp(LocalDateTime.now());
        return metadata;
    }
}
