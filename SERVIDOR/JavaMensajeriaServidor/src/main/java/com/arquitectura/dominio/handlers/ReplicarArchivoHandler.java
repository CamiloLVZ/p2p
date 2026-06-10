package com.arquitectura.dominio.handlers;

import com.arquitectura.aplicacion.router.Handler;
import com.arquitectura.aplicacion.sesion.GestorServidoresPeer;
import com.arquitectura.infraestructura.seguridad.CryptoUtil;
import com.arquitectura.dominio.repositorios.ArchivoRecibidoRepository;
import com.arquitectura.dominio.repositorios.JpaArchivoRecibidoRepository;
import com.arquitectura.mensajeria.Mensaje;
import com.arquitectura.mensajeria.Metadata;
import com.arquitectura.mensajeria.Respuesta;
import com.arquitectura.mensajeria.enums.Accion;
import com.arquitectura.mensajeria.enums.Estado;
import com.arquitectura.mensajeria.enums.TipoMensaje;
import com.arquitectura.mensajeria.payload.PayloadReplicarArchivo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Logger;

public class ReplicarArchivoHandler implements Handler<PayloadReplicarArchivo> {

    private static final Logger LOGGER = Logger.getLogger(ReplicarArchivoHandler.class.getName());
    private static final Path DIRECTORIO_DESTINO = Path.of("archivos-recibidos");
    private final ArchivoRecibidoRepository archivoRecibidoRepository = new JpaArchivoRecibidoRepository();

    @Override
    public Respuesta<?> handle(Mensaje<PayloadReplicarArchivo> mensaje) {
        PayloadReplicarArchivo payload = mensaje.getPayload();
        if (payload == null || payload.getId() == null || payload.getId().isBlank()) {
            Respuesta<?> error = new Respuesta<>();
            error.setEstado(Estado.ERROR);
            return error;
        }

        // Idempotencia: si ya existe, ignorar silenciosamente
        if (archivoRecibidoRepository.existePorId(payload.getId())) {
            LOGGER.fine(() -> "Archivo replicado ya existente, ignorando: " + payload.getId());
            return crearRespuestaExito("Archivo ya existe (idempotente): " + payload.getId());
        }

        LocalDateTime fechaRecepcion = payload.getFechaRecepcion() != null
                ? payload.getFechaRecepcion()
                : LocalDateTime.now();
        String servidorOrigen = payload.getServidorOrigen();

        // Decodificar contenido cifrado y escribir a disco
        String rutaFinal = "";
        try {
            if (payload.getContenidoCifrado() != null && !payload.getContenidoCifrado().isBlank()) {
                byte[] contenidoBytes = CryptoUtil.aesDecryptBase64(payload.getContenidoCifrado());
                Path rutaArchivo = guardarArchivo(payload.getNombreArchivo(), payload.getExtension(), contenidoBytes);
                rutaFinal = rutaArchivo.toAbsolutePath().toString();
                LOGGER.info(() -> "Archivo replicado guardado en disco: " + rutaArchivo.toAbsolutePath());
            }
        } catch (IOException e) {
            LOGGER.severe(() -> "Error al guardar archivo replicado " + payload.getNombreArchivo() + ": " + e.getMessage());
            Respuesta<?> error = new Respuesta<>();
            error.setEstado(Estado.ERROR);
            return error;
        }

        String clientIdDestino = payload.getClientIdDestino();

        archivoRecibidoRepository.guardar(
                payload.getId(),
                payload.getRemitente(),
                servidorOrigen != null ? servidorOrigen : "peer-desconocido",
                payload.getNombreArchivo(),
                payload.getExtension(),
                rutaFinal,
                payload.getHashSha256(),
                payload.getContenidoCifrado(),
                payload.getTamano(),
                fechaRecepcion,
                servidorOrigen,
                clientIdDestino
        );

        LOGGER.info(() -> "Archivo replicado persistido: id=" + payload.getId()
                + " | nombre=" + payload.getNombreArchivo()
                + " | origen=" + servidorOrigen
                + " | destinatario=" + clientIdDestino);

        // Fan-out solo si es broadcast (clientIdDestino == null)
        if (clientIdDestino == null || clientIdDestino.isBlank()) {
            GestorServidoresPeer gestorPeers = GestorServidoresPeer.getInstance();
            if (!gestorPeers.obtenerPeersConectados().isEmpty()) {
                Mensaje<PayloadReplicarArchivo> msgReplica = construirMensajeReplica(payload);
                gestorPeers.enviarATodos(msgReplica);
                LOGGER.info(() -> "Archivo replicado propagado a peers adicionales: id=" + payload.getId());
            }
        }

        return crearRespuestaExito("Archivo replicado: " + payload.getId());
    }

    @Override
    public Class<PayloadReplicarArchivo> getPayloadClass() {
        return PayloadReplicarArchivo.class;
    }

    private Path guardarArchivo(String nombreArchivo, String extension, byte[] contenido) throws IOException {
        Files.createDirectories(DIRECTORIO_DESTINO);
        String nombreCompleto = construirNombreArchivo(nombreArchivo, extension);
        Path rutaArchivo = resolverRutaSinColision(nombreCompleto);
        Files.write(rutaArchivo, contenido);
        return rutaArchivo;
    }

    private String construirNombreArchivo(String nombre, String extension) {
        if (nombre == null) nombre = "archivo-replicado";
        if (extension == null || extension.isBlank() || nombre.endsWith("." + extension)) {
            return nombre;
        }
        return nombre + "." + extension;
    }

    private Path resolverRutaSinColision(String nombreArchivo) {
        Path rutaInicial = DIRECTORIO_DESTINO.resolve(nombreArchivo);
        if (!Files.exists(rutaInicial)) {
            return rutaInicial;
        }

        String extension = extraerExtension(nombreArchivo);
        String nombreBase = extraerNombreBase(nombreArchivo);
        int contador = 1;
        Path rutaCandidata = rutaInicial;
        while (Files.exists(rutaCandidata)) {
            String nombreConSufijo = extension.isBlank()
                    ? nombreBase + " (" + contador + ")"
                    : nombreBase + " (" + contador + ")." + extension;
            rutaCandidata = DIRECTORIO_DESTINO.resolve(nombreConSufijo);
            contador++;
        }
        return rutaCandidata;
    }

    private String extraerNombreBase(String nombreArchivoCompleto) {
        int ultimoPunto = nombreArchivoCompleto.lastIndexOf('.');
        if (ultimoPunto <= 0) {
            return nombreArchivoCompleto;
        }
        return nombreArchivoCompleto.substring(0, ultimoPunto);
    }

    private String extraerExtension(String nombreArchivoCompleto) {
        int ultimoPunto = nombreArchivoCompleto.lastIndexOf('.');
        if (ultimoPunto <= 0 || ultimoPunto == nombreArchivoCompleto.length() - 1) {
            return "";
        }
        return nombreArchivoCompleto.substring(ultimoPunto + 1).toLowerCase(Locale.ROOT);
    }

    private Respuesta<String> crearRespuestaExito(String texto) {
        Mensaje<String> mensajeRespuesta = new Mensaje<>();
        mensajeRespuesta.setTipo(TipoMensaje.RESPONSE);
        mensajeRespuesta.setAccion(Accion.REPLICAR_ARCHIVO);
        mensajeRespuesta.setMetadata(crearMetadata());
        mensajeRespuesta.setPayload(texto);

        Respuesta<String> respuesta = new Respuesta<>();
        respuesta.setEstado(Estado.EXITO);
        respuesta.setMensaje(mensajeRespuesta);
        return respuesta;
    }

    private Mensaje<PayloadReplicarArchivo> construirMensajeReplica(PayloadReplicarArchivo payload) {
        Mensaje<PayloadReplicarArchivo> msg = new Mensaje<>();
        msg.setTipo(TipoMensaje.REQUEST);
        msg.setAccion(Accion.REPLICAR_ARCHIVO);
        msg.setMetadata(crearMetadata());
        msg.setPayload(payload);
        return msg;
    }

    private Metadata crearMetadata() {
        Metadata metadata = new Metadata();
        metadata.setIdMensaje(UUID.randomUUID().toString());
        metadata.setTimestamp(LocalDateTime.now());
        return metadata;
    }
}
