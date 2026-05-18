package com.arquitectura.dominio.handlers;

import com.arquitectura.aplicacion.router.Handler;
import com.arquitectura.aplicacion.sesion.GestorServidoresPeer;
import com.arquitectura.aplicacion.sesion.GestorSesiones;
import com.arquitectura.aplicacion.sesion.SesionCliente;
import com.arquitectura.dominio.modelo.ArchivoRecibidoModel;
import com.arquitectura.dominio.modelo.MensajeModel;
import com.arquitectura.dominio.repositorios.ArchivoRecibidoRepository;
import com.arquitectura.dominio.repositorios.JpaArchivoRecibidoRepository;
import com.arquitectura.dominio.repositorios.JpaMensajeRepository;
import com.arquitectura.dominio.repositorios.MensajeRepository;
import com.arquitectura.mensajeria.Mensaje;
import com.arquitectura.mensajeria.Metadata;
import com.arquitectura.mensajeria.Respuesta;
import com.arquitectura.mensajeria.enums.Accion;
import com.arquitectura.mensajeria.enums.Estado;
import com.arquitectura.mensajeria.enums.TipoMensaje;
import com.arquitectura.mensajeria.payload.PayloadClienteRemoto;
import com.arquitectura.mensajeria.payload.PayloadRegistrarServidor;
import com.arquitectura.mensajeria.payload.PayloadReplicarClientes;
import com.arquitectura.aplicacion.replicacion.ReplicadorArchivos;
import com.arquitectura.aplicacion.sesion.ConexionPeer;
import com.arquitectura.mensajeria.payload.PayloadServidorInfo;
import com.arquitectura.mensajeria.payload.PayloadSincronizarEstado;
import com.arquitectura.mensajeria.payload.PayloadSincronizarEstado.MensajeSync;
import com.arquitectura.mensajeria.payload.PayloadSincronizarEstado.ArchivoSync;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

public class RegistrarServidorHandler implements Handler<PayloadRegistrarServidor> {

    private static final Logger LOGGER = Logger.getLogger(RegistrarServidorHandler.class.getName());

    // Executor dedicado para no bloquear el hilo del handler mientras se envía el sync
    private static final ExecutorService SYNC_EXECUTOR = Executors.newCachedThreadPool();

    private final MensajeRepository mensajeRepo = new JpaMensajeRepository();
    private final ArchivoRecibidoRepository archivoRepo = new JpaArchivoRecibidoRepository();

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

        // ---- Replicar lista de clientes locales al peer que se está registrando ----
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

        // ---- Sincronización de estado en background ----
        // El peer recién conectado/reconectado puede tener datos desactualizados.
        // Le enviamos TODO nuestro historial; él aplica idempotencia y solo persiste lo nuevo.
        final String peerDestino = servidorId;
        SYNC_EXECUTOR.submit(() -> enviarSincronizacion(gestor, peerDestino));

        // ---- Construir respuesta con peers conocidos para descubrimiento transitivo ----
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

    // -------------------------------------------------------------------------
    // Sincronización de estado completo al peer que se registró
    // -------------------------------------------------------------------------

    private void enviarSincronizacion(GestorServidoresPeer gestor, String peerDestino) {
        try {
            // Cargar historial local completo
            List<MensajeModel> mensajes = mensajeRepo.listarTodos();
            List<ArchivoRecibidoModel> archivos = archivoRepo.listarTodos();

            if (mensajes.isEmpty() && archivos.isEmpty()) {
                LOGGER.fine(() -> "Sin datos locales para sincronizar con peer " + peerDestino);
                return;
            }

            // Mapear modelos JPA → DTOs de sync
            List<MensajeSync> mensajesSync = new ArrayList<>(mensajes.size());
            for (MensajeModel m : mensajes) {
                MensajeSync dto = new MensajeSync();
                dto.setId(m.getId());
                dto.setAutor(m.getAutor());
                dto.setIpRemitente(m.getIpRemitente());
                dto.setContenido(m.getContenido());
                dto.setHashSha256(m.getHashSha256());
                dto.setContenidoCifrado(m.getContenidoCifrado());
                dto.setFechaEnvio(m.getFechaEnvio());
                dto.setServidorOrigen(m.getServidorOrigen());
                dto.setDestinatario(m.getDestinatario());
                mensajesSync.add(dto);
            }

            List<ArchivoSync> archivosSync = new ArrayList<>(archivos.size());
            for (ArchivoRecibidoModel a : archivos) {
                ArchivoSync dto = new ArchivoSync();
                dto.setId(a.getId());
                dto.setRemitente(a.getRemitente());
                dto.setIpRemitente(a.getIpRemitente());
                dto.setNombreArchivo(a.getNombreArchivo());
                dto.setExtension(a.getExtension());
                dto.setRutaArchivo(a.getRutaArchivo());
                dto.setHashSha256(a.getHashSha256());
                dto.setContenidoCifrado(a.getContenidoCifrado());
                dto.setTamano(a.getTamano());
                dto.setFechaRecepcion(a.getFechaRecepcion());
                dto.setServidorOrigen(a.getServidorOrigen());
                dto.setDestinatario(a.getDestinatario());
                archivosSync.add(dto);
            }

            PayloadSincronizarEstado syncPayload = new PayloadSincronizarEstado();
            syncPayload.setServidorOrigen(gestor.getServidorId());
            syncPayload.setMensajes(mensajesSync);
            syncPayload.setArchivos(archivosSync);

            Mensaje<PayloadSincronizarEstado> msgSync = new Mensaje<>();
            msgSync.setTipo(TipoMensaje.REQUEST);
            msgSync.setAccion(Accion.SINCRONIZAR_ESTADO);
            msgSync.setMetadata(crearMetadata());
            msgSync.setPayload(syncPayload);

            boolean ok = gestor.enviarAPeer(peerDestino, msgSync);
            if (ok) {
                LOGGER.info(() -> "Sincronización de metadatos enviada a " + peerDestino
                        + " | mensajes=" + mensajesSync.size()
                        + " | archivos=" + archivosSync.size());

                // ---- Stream de bytes de archivos físicos ----
                // Los metadatos ya los recibió el peer vía SINCRONIZAR_ESTADO.
                // Ahora enviamos los bytes de cada archivo que exista en disco de este servidor.
                // El peer (en ReplicarArchivoStreamHandler + GestorTransferencias) aplica
                // idempotencia: si ya tiene el registro en DB, ignora el stream.
                ConexionPeer peer = gestor.obtenerPeers().stream()
                        .filter(p -> p.getConfig().getServidorId().equals(peerDestino))
                        .findFirst()
                        .orElse(null);

                if (peer != null) {
                    ReplicadorArchivos replicador = new ReplicadorArchivos();
                    int archivosStreamados = 0;
                    for (ArchivoRecibidoModel archivo : archivos) {
                        if (archivo.getRutaArchivo() == null || archivo.getRutaArchivo().isBlank()) continue;
                        Path ruta = Path.of(archivo.getRutaArchivo());
                        if (!Files.exists(ruta)) {
                            // El archivo físico no está en este servidor (vino de otro peer)
                            // — no podemos streamearlo, el peer lo buscará en otro nodo
                            LOGGER.fine(() -> "Archivo físico no disponible para sync: " + ruta);
                            continue;
                        }
                        replicador.replicarAPeer(archivo, gestor.getServidorId(), peer);
                        archivosStreamados++;
                    }
                    final int streamed = archivosStreamados;
                    LOGGER.info(() -> "Stream de bytes iniciado para " + streamed
                            + " archivos hacia " + peerDestino);
                }
            } else {
                LOGGER.warning(() -> "No se pudo enviar sincronización a peer " + peerDestino);
            }

        } catch (Exception e) {
            LOGGER.warning(() -> "Error al preparar sincronización para " + peerDestino + ": " + e.getMessage());
        }
    }

    private Metadata crearMetadata() {
        Metadata metadata = new Metadata();
        metadata.setIdMensaje(UUID.randomUUID().toString());
        metadata.setTimestamp(LocalDateTime.now());
        return metadata;
    }
}
