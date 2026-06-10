package com.arquitectura.aplicacion.sesion;

import com.arquitectura.infraestructura.serializacion.JsonUtil;
import com.arquitectura.mensajeria.Mensaje;
import com.arquitectura.mensajeria.Metadata;
import com.arquitectura.mensajeria.enums.Accion;
import com.arquitectura.mensajeria.enums.TipoMensaje;
import com.arquitectura.mensajeria.payload.PayloadClienteRemoto;
import com.arquitectura.mensajeria.payload.PayloadRegistrarServidor;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Gestor singleton de conexiones con servidores peer (federacion).
 *
 * <p>Controla:
 * - el mapa de peers con su estado de circuito,
 * - el envio (unicast y broadcast) de mensajes S2S por TCP,
 * - el cache de clientes remotos replicados por otros servidores.</p>
 */
public class GestorServidoresPeer {

    private static final Logger LOGGER = Logger.getLogger(GestorServidoresPeer.class.getName());

    private static GestorServidoresPeer instancia;

    private final Map<String, ConexionPeer> peers = new ConcurrentHashMap<>();
    private final Map<String, List<PayloadClienteRemoto>> cacheClientesRemotos = new ConcurrentHashMap<>();

    private String servidorId;
    private int servidorPuerto;
    private String servidorHost; // IP pública explícita (configurada en application.properties)

    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final ScheduledExecutorService reconexionScheduler = Executors.newSingleThreadScheduledExecutor();

    private GestorServidoresPeer() {
    }

    public static synchronized GestorServidoresPeer getInstance() {
        if (instancia == null) {
            instancia = new GestorServidoresPeer();
        }
        return instancia;
    }

    /**
     * Inicializa el gestor con la identidad de este servidor y la lista de peers conocidos.
     * Debe llamarse una unica vez desde Main.java.
     */
    public static synchronized void inicializar(String servidorId, int puerto,
            List<PeerConfig> peerConfigs, int maxIntentos) {
        inicializar(servidorId, puerto, null, peerConfigs, maxIntentos);
    }

    public static synchronized void inicializar(String servidorId, int puerto, String host,
            List<PeerConfig> peerConfigs, int maxIntentos) {
        GestorServidoresPeer g = getInstance();
        g.servidorId = servidorId;
        g.servidorPuerto = puerto;
        g.servidorHost = host; // puede ser null → fallback automático
        for (PeerConfig config : peerConfigs) {
            ConexionPeer conexion = new ConexionPeer(config, maxIntentos);
            g.peers.put(config.getServidorId(), conexion);
        }
        LOGGER.info("GestorServidoresPeer inicializado. servidorId=" + servidorId
                + " puerto=" + puerto + " peers=" + peerConfigs.size());
    }

    // -------------------------------------------------------------------------
    // Conexion inicial
    // -------------------------------------------------------------------------

    /**
     * Intenta conectarse a todos los peers conocidos en background.
     * Los que ya esten CLOSED se omiten; HALF_OPEN y OPEN se prueban.
     */
    public void conectarAPeers() {
        for (ConexionPeer peer : peers.values()) {
            final ConexionPeer p = peer;
            executor.submit(() -> {
                try {
                    conectarConHandshake(p);
                } catch (Exception e) {
                    p.registrarFallo();
                    LOGGER.log(Level.WARNING, "Error conectando a peer " + p.getConfig().getServidorId(), e);
                }
            });
        }
        iniciarReconexionPeriodica();
    }

    private void iniciarReconexionPeriodica() {
        reconexionScheduler.scheduleAtFixedRate(() -> {
            for (ConexionPeer peer : peers.values()) {
                if (peer.estaConectado()) continue; // CLOSED — no necesita reconexion
                final ConexionPeer p = peer;
                // Backoff: cada 3 intentos fallidos, esperar un ciclo mas antes de reintentar
                // Esto evita saturar logs con peers permanentemente inalcanzables
                if (p.getIntentosReconexion() > 0 && p.getIntentosReconexion() % 3 != 0) {
                    LOGGER.fine("Backoff reconexion para " + p.getConfig().getServidorId()
                            + " (intento " + p.getIntentosReconexion() + ")");
                    continue;
                }
                executor.submit(() -> {
                    try {
                        LOGGER.info("Reconexion periodica a peer: " + p.getConfig().getServidorId()
                                + " @ " + p.getConfig().getHost() + ":" + p.getConfig().getPuerto());
                        conectarConHandshake(p);
                    } catch (Exception e) {
                        p.registrarFallo();
                        LOGGER.log(Level.WARNING, "Error en reconexion periodica a peer "
                                + p.getConfig().getServidorId(), e);
                    }
                });
            }
        }, 15, 15, TimeUnit.SECONDS);
    }

    private void conectarConHandshake(ConexionPeer peer) {
        PayloadRegistrarServidor handshake = new PayloadRegistrarServidor();
        handshake.setServidorId(this.servidorId);
        handshake.setHost(resolverIpLocal());
        handshake.setPuerto(this.servidorPuerto);
        handshake.setVersion("1.0");

        Metadata meta = new Metadata();
        meta.setIdMensaje(UUID.randomUUID().toString());
        meta.setTimestamp(LocalDateTime.now());

        Mensaje<PayloadRegistrarServidor> msg = new Mensaje<>();
        msg.setTipo(TipoMensaje.REQUEST);
        msg.setAccion(Accion.REGISTRAR_SERVIDOR);
        msg.setMetadata(meta);
        msg.setPayload(handshake);

        String respuestaJson = enviarMensajeAPeerYLeerRespuesta(peer, msg);
        if (respuestaJson != null) {
            peer.marcarConectado();
            LOGGER.info(() -> "Handshake OK con " + peer.getConfig().getServidorId());
            descubrirPeersTransitivos(respuestaJson);
        } else {
            peer.registrarFallo();
            LOGGER.warning(() -> "Handshake fallido con " + peer.getConfig().getServidorId());
        }
    }

    private void descubrirPeersTransitivos(String respuestaJson) {
        try {
            JsonNode root = JsonUtil.getMapper().readTree(respuestaJson);
            JsonNode payload = root.path("mensaje").path("payload");

            if (payload.isMissingNode() || payload.isNull()) return;

            JsonNode peersNode = payload.path("peersConocidos");
            if (peersNode.isMissingNode() || !peersNode.isArray()) return;

            for (JsonNode peerNode : peersNode) {
                String pid = peerNode.path("servidorId").asText();
                String phost = peerNode.path("host").asText();
                int ppuerto = peerNode.path("puerto").asInt();

                if (pid.isBlank() || phost.isBlank() || ppuerto <= 0) continue;
                if (pid.equals(this.servidorId)) continue;
                if (peers.containsKey(pid)) continue;

                LOGGER.info(() -> "Peer transitivo descubierto: " + pid + " @ " + phost + ":" + ppuerto);
                PeerConfig config = new PeerConfig(pid, phost, ppuerto);
                ConexionPeer nuevoPeer = new ConexionPeer(config, 5);
                peers.put(pid, nuevoPeer);
                executor.submit(() -> {
                    try {
                        conectarConHandshake(nuevoPeer);
                    } catch (Exception e) {
                        nuevoPeer.registrarFallo();
                        LOGGER.warning("Error conectando a peer transitivo " + pid + ": " + e.getMessage());
                    }
                });
            }
        } catch (Exception e) {
            LOGGER.fine("No se pudo parsear peers transitivos de la respuesta: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Broadcast
    // -------------------------------------------------------------------------

    /**
     * Envia el mensaje a todos los peers. Si un peer esta OPEN intenta rehabilitarlo primero.
     * Fire-and-forget (asincrono).
     */
    public void enviarATodos(Mensaje<?> mensaje) {
        for (ConexionPeer peer : peers.values()) {
            final ConexionPeer p = peer;
            executor.submit(() -> {
                try {
                    // Intentar siempre — cada mensaje abre su propio socket.
                    // El exito rehabilita el peer; el fallo lo registra.
                    boolean ok = enviarMensajeAPeer(p, mensaje);
                    if (ok) {
                        p.marcarConectado();
                    } else {
                        p.registrarFallo();
                        LOGGER.warning("Fallo al enviar a peer " + p.getConfig().getServidorId());
                    }
                } catch (Exception e) {
                    p.registrarFallo();
                    LOGGER.log(Level.WARNING, "Error enviando a peer " + p.getConfig().getServidorId(), e);
                }
            });
        }
    }

    // -------------------------------------------------------------------------
    // Unicast
    // -------------------------------------------------------------------------

    /**
     * Envia el mensaje a un peer especifico.
     *
     * @return true si el envio fue exitoso.
     */
    public boolean enviarAPeer(String servidorId, Mensaje<?> mensaje) {
        ConexionPeer peer = peers.get(servidorId);
        if (peer == null) {
            LOGGER.warning("Peer no encontrado: " + servidorId);
            return false;
        }

        // Intentar envio directo siempre (cada mensaje abre su propio socket).
        // Si el peer estaba OPEN y el envio tiene exito, se rehabilita automaticamente.
        boolean ok = enviarMensajeAPeer(peer, mensaje);
        if (ok) {
            peer.marcarConectado();
        } else {
            peer.registrarFallo();
            LOGGER.warning("Fallo al enviar mensaje a peer " + servidorId);
        }
        return ok;
    }

    // -------------------------------------------------------------------------
    // Circuito
    // -------------------------------------------------------------------------

    private boolean intentarRehabilitacion(ConexionPeer peer) {
        LOGGER.info("Intentando rehabilitar peer " + peer.getConfig().getServidorId());
        conectarConHandshake(peer);
        return peer.estaConectado();
    }

    private boolean enviarMensajeAPeer(ConexionPeer peer, Mensaje<?> mensaje) {
        return enviarMensajeAPeerYLeerRespuesta(peer, mensaje) != null;
    }

    /**
     * Abre una conexion TCP al peer, serializa el mensaje como JSON, escribe una linea,
     * lee la respuesta y cierra el socket.
     *
     * @param peer    peer destino
     * @param mensaje mensaje a enviar
     * @return JSON de respuesta, o null si la operacion fallo
     */
    private String enviarMensajeAPeerYLeerRespuesta(ConexionPeer peer, Mensaje<?> mensaje) {
        PeerConfig config = peer.getConfig();
        try {
            Socket socket = new Socket();
            socket.connect(new java.net.InetSocketAddress(config.getHost(), config.getPuerto()), 5_000);
            socket.setSoTimeout(8_000);
            try (socket;
                 PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
                 BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

                String json = JsonUtil.toJson(mensaje);
                writer.println(json);
                writer.flush();

                String respuesta = reader.readLine();
                LOGGER.fine("Respuesta de " + config.getServidorId() + ": " + respuesta);
                return respuesta;
            }
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Error de red con peer " + config.getServidorId() + ": " + e.getMessage());
            return null;
        }
    }

    // -------------------------------------------------------------------------
    // Unicast sincrono (proxy)
    // -------------------------------------------------------------------------

    /**
     * Envia un mensaje a un peer especifico y espera su respuesta (sincrono, para proxy).
     * Abre socket, escribe JSON, lee respuesta, cierra socket.
     *
     * @return JSON de respuesta del peer, o null si falla.
     */
    public String enviarAPeerYEsperar(String servidorId, Mensaje<?> mensaje) {
        ConexionPeer peer = peers.get(servidorId);
        if (peer == null) {
            LOGGER.warning("enviarAPeerYEsperar: peer no encontrado: " + servidorId);
            return null;
        }
        if (peer.getEstado() == EstadoPeer.OPEN) {
            boolean rehabilitado = intentarRehabilitacion(peer);
            if (!rehabilitado) {
                LOGGER.warning("enviarAPeerYEsperar: peer OPEN no rehabilitado: " + servidorId);
                return null;
            }
        }
        try {
            String json = JsonUtil.toJson(mensaje);
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(
                        peer.getConfig().getHost(), peer.getConfig().getPuerto()), 5000);
                socket.setSoTimeout(30000);
                BufferedWriter w = new BufferedWriter(
                        new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
                BufferedReader r = new BufferedReader(
                        new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                w.write(json);
                w.newLine();
                w.flush();
                return r.readLine();
            }
        } catch (Exception e) {
            LOGGER.warning(() -> "enviarAPeerYEsperar falló a " + servidorId + ": " + e.getMessage());
            peer.registrarFallo();
            return null;
        }
    }

    // -------------------------------------------------------------------------
    // Gestion de estado de peers (llamado desde handlers)
    // -------------------------------------------------------------------------

    /**
     * Marca un peer como conectado (CLOSED). Llamado por RegistrarServidorHandler
     * cuando un peer remoto se presenta con REGISTRAR_SERVIDOR.
     */
    public void marcarPeerConectado(String servidorId) {
        marcarPeerConectado(servidorId, null, -1);
    }

    /**
     * Marca un peer como conectado. Si el peer no estaba en el mapa (llegó por handshake
     * sin estar en application.properties), lo registra dinámicamente.
     */
    public void marcarPeerConectado(String servidorId, String host, int puerto) {
        ConexionPeer peer = peers.get(servidorId);
        if (peer == null && host != null && puerto > 0) {
            // Peer desconocido que se presentó — registrarlo dinámicamente
            PeerConfig config = new PeerConfig(servidorId, host, puerto);
            peer = new ConexionPeer(config, 5);
            peers.put(servidorId, peer);
            LOGGER.info("Peer registrado dinámicamente: " + servidorId + " @ " + host + ":" + puerto);
        }
        if (peer != null) {
            peer.marcarConectado();
            LOGGER.info("Peer marcado como conectado: " + servidorId);
        } else {
            LOGGER.warning("marcarPeerConectado: peer no encontrado y sin datos de red: " + servidorId);
        }
    }

    /**
     * Marca un peer como desconectado (OPEN). Llamado por DesconectarServidorHandler
     * cuando un peer remoto anuncia su desconexion.
     */
    public void marcarPeerDesconectado(String servidorId) {
        ConexionPeer peer = peers.get(servidorId);
        if (peer != null) {
            peer.registrarFallo();
            LOGGER.info("Peer marcado como desconectado: " + servidorId);
        } else {
            LOGGER.warning("marcarPeerDesconectado: peer no encontrado en mapa: " + servidorId);
        }
    }

    // -------------------------------------------------------------------------
    // Consultas de estado
    // -------------------------------------------------------------------------

    /** @return lista no modificable de todos los peers. */
    public List<ConexionPeer> obtenerPeers() {
        return Collections.unmodifiableList(new ArrayList<>(peers.values()));
    }

    /** @return lista no modificable de peers en estado CLOSED (activos). */
    public List<ConexionPeer> obtenerPeersConectados() {
        List<ConexionPeer> conectados = new ArrayList<>();
        for (ConexionPeer peer : peers.values()) {
            if (peer.estaConectado()) {
                conectados.add(peer);
            }
        }
        return Collections.unmodifiableList(conectados);
    }

    // -------------------------------------------------------------------------
    // Cache de clientes remotos
    // -------------------------------------------------------------------------

    /**
     * Almacena (o reemplaza) la lista de clientes replicados por un servidor origen.
     */
    public void actualizarCacheClientes(String servidorOrigen, List<PayloadClienteRemoto> clientes) {
        cacheClientesRemotos.put(servidorOrigen, new ArrayList<>(clientes));
        LOGGER.fine("Cache de clientes actualizado para " + servidorOrigen
                + " (" + clientes.size() + " clientes)");
    }

    /** @return lista plana de todos los clientes remotos conocidos. */
    public List<PayloadClienteRemoto> obtenerTodosClientesRemotos() {
        List<PayloadClienteRemoto> todos = new ArrayList<>();
        for (List<PayloadClienteRemoto> lista : cacheClientesRemotos.values()) {
            todos.addAll(lista);
        }
        return Collections.unmodifiableList(todos);
    }

    /**
     * Elimina la entrada del cache cuando un peer se desconecta.
     */
    public void invalidarCacheClientes(String servidorOrigen) {
        cacheClientesRemotos.remove(servidorOrigen);
        LOGGER.fine("Cache de clientes invalidado para " + servidorOrigen);
    }

    // -------------------------------------------------------------------------
    // Getters de identidad
    // -------------------------------------------------------------------------

    public String getServidorId() {
        return servidorId;
    }

    public int getServidorPuerto() {
        return servidorPuerto;
    }

    // -------------------------------------------------------------------------
    // Utilidades de red
    // -------------------------------------------------------------------------

    private String resolverIpLocal() {
        return resolverIpLocalPublic();
    }

    public String resolverIpLocalPublic() {
        // Si el admin configuró server.host explícitamente, usarla siempre.
        // Evita que máquinas con VPN/múltiples NICs anuncien la IP incorrecta.
        if (servidorHost != null && !servidorHost.isBlank()) {
            return servidorHost;
        }
        try {
            java.util.Enumeration<java.net.NetworkInterface> interfaces =
                    java.net.NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                java.net.NetworkInterface ni = interfaces.nextElement();
                if (ni.isLoopback() || !ni.isUp()) continue;
                java.util.Enumeration<java.net.InetAddress> addrs = ni.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    java.net.InetAddress addr = addrs.nextElement();
                    if (addr instanceof java.net.Inet4Address && !addr.isLoopbackAddress()) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warning("No se pudo resolver IP local: " + e.getMessage());
        }
        return "localhost";
    }
}
