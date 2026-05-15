package com.arquitectura.dominio.handlers;

import com.arquitectura.aplicacion.ContextoSolicitud;
import com.arquitectura.aplicacion.replicacion.ReplicadorArchivos;
import com.arquitectura.aplicacion.router.Handler;
import com.arquitectura.aplicacion.sesion.GestorSesiones;
import com.arquitectura.aplicacion.sesion.GestorServidoresPeer;
import com.arquitectura.aplicacion.sesion.ResultadoValidacionSesion;
import com.arquitectura.aplicacion.transferencia.GestorTransferencias;
import com.arquitectura.aplicacion.transferencia.GestorTransferencias.EstadoTransferencia;
import com.arquitectura.dominio.repositorios.ArchivoRecibidoRepository;
import com.arquitectura.dominio.repositorios.JpaArchivoRecibidoRepository;
import com.arquitectura.infraestructura.seguridad.CryptoUtil;
import com.arquitectura.mensajeria.ErrorDetalle;
import com.arquitectura.mensajeria.Mensaje;
import com.arquitectura.mensajeria.Metadata;
import com.arquitectura.mensajeria.Respuesta;
import com.arquitectura.mensajeria.enums.Accion;
import com.arquitectura.mensajeria.enums.Estado;
import com.arquitectura.mensajeria.enums.TipoMensaje;
import com.arquitectura.mensajeria.payload.PayloadFinalizarStream;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Maneja el mensaje de control FINALIZAR_STREAM.
 *
 * 1. Valida que exista la transferencia activa con ese transferId.
 * 2. Compara el hash SHA-256 del cliente contra el calculado incrementalmente.
 * 3. Mueve el archivo temporal a su nombre definitivo.
 * 4. Persiste el registro en base de datos.
 * 5. Limpia el GestorTransferencias.
 *
 * NOTA: el cifrado AES del archivo completo se omite intencionalmente para archivos
 * grandes — cifrar varios GB en memoria provoca OOM. Solo se guarda el hash.
 */
public class FinalizarStreamHandler implements Handler<PayloadFinalizarStream> {

    private static final Logger LOGGER = Logger.getLogger(FinalizarStreamHandler.class.getName());
    private static final Path DIRECTORIO_DESTINO = Path.of("archivos-recibidos");

    private final GestorSesiones gestorSesiones = GestorSesiones.getInstance();
    private final GestorTransferencias gestorTransferencias = GestorTransferencias.getInstance();
    private final ArchivoRecibidoRepository repositorio = new JpaArchivoRecibidoRepository();

    @Override
    public Respuesta<?> handle(Mensaje<PayloadFinalizarStream> mensaje) {
        PayloadFinalizarStream payload = mensaje.getPayload();
        String remitente = resolverRemitente(mensaje);

        ResultadoValidacionSesion validacion = gestorSesiones.validarSesion(
                remitente,
                ContextoSolicitud.obtenerIpRemitente(),
                puertoRemitente(),
                ContextoSolicitud.obtenerProtocolo()
        );
        if (!validacion.exito()) {
            return crearError("SESION_INVALIDA", validacion.mensaje());
        }

        String transferId = payload.getTransferId();
        EstadoTransferencia estado = gestorTransferencias.obtener(transferId);
        if (estado == null) {
            return crearError("TRANSFERENCIA_NO_ENCONTRADA",
                    "No existe una transferencia activa con id: " + transferId);
        }

        // Validar hash
        String hashServidor = estado.hashFinalBase64();
        String hashCliente = payload.getHashSha256();
        if (!hashServidor.equals(hashCliente)) {
            LOGGER.warning(() -> "Hash mismatch en transferencia " + transferId
                    + " | cliente: " + hashCliente + " | servidor: " + hashServidor);
            estado.eliminarArchivoParcial();
            gestorTransferencias.eliminar(transferId);
            return crearError("HASH_INVALIDO",
                    "El hash SHA-256 del archivo no coincide. La transferencia fue abortada.");
        }

        try {
            // Mover el .tmp al nombre definitivo
            Path rutaFinal = resolverRutaFinal(estado);
            Files.move(estado.getRutaTemporal(), rutaFinal);

            String ipRemitente = ContextoSolicitud.obtenerIpRemitente();
            String nombreFinal = rutaFinal.getFileName().toString();

            repositorio.guardar(
                    transferId,
                    remitente,
                    ipRemitente,
                    extraerNombreBase(nombreFinal),
                    estado.getExtension(),
                    rutaFinal.toAbsolutePath().toString(),
                    hashServidor,
                    "",
                    estado.getTamanoTotal(),
                    LocalDateTime.now(),
                    null
            );

            // Replication — fire-and-forget (solo si no es unicast a cliente específico)
            if (estado.getClientIdDestino() == null) {
                repositorio.buscarPorId(transferId).ifPresent(modelo ->
                        new ReplicadorArchivos().replicar(modelo, GestorServidoresPeer.getInstance().getServidorId()));
            } else {
                LOGGER.info(() -> "Stream unicast a [" + estado.getClientIdDestino()
                        + "] — replicación S2S omitida para transferencia " + transferId);
            }

            gestorTransferencias.eliminar(transferId);

            LOGGER.info(() -> "Stream finalizado: " + transferId
                    + " | archivo: " + nombreFinal
                    + " | bytes: " + estado.getBytesRecibidos()
                    + " | chunks: " + estado.getChunksRecibidos()
                    + " | remitente: " + remitente);

            return crearRespuestaExitosa(nombreFinal, rutaFinal);

        } catch (IOException e) {
            LOGGER.severe(() -> "Error finalizando transferencia " + transferId + ": " + e.getMessage());
            gestorTransferencias.eliminar(transferId);
            return crearError("ERROR_DISCO", "Error al finalizar el archivo: " + e.getMessage());
        } catch (Exception e) {
            LOGGER.severe(() -> "Error inesperado al persistir transferencia " + transferId + ": " + e.getMessage());
            gestorTransferencias.eliminar(transferId);
            return crearError("ERROR_INTERNO", "Error al registrar el archivo: " + e.getMessage());
        }
    }

    @Override
    public Class<PayloadFinalizarStream> getPayloadClass() {
        return PayloadFinalizarStream.class;
    }

    private Path resolverRutaFinal(EstadoTransferencia estado) {
        String nombre = estado.getNombreArchivo();
        String ext = estado.getExtension();
        String nombreCompleto = (ext != null && !ext.isBlank() && !nombre.endsWith("." + ext))
                ? nombre + "." + ext : nombre;

        Path candidato = DIRECTORIO_DESTINO.resolve(nombreCompleto);
        if (!Files.exists(candidato)) return candidato;

        String nombreBase = extraerNombreBase(nombreCompleto);
        int contador = 1;
        while (Files.exists(candidato)) {
            String sufijo = (ext != null && !ext.isBlank())
                    ? nombreBase + " (" + contador + ")." + ext
                    : nombreBase + " (" + contador + ")";
            candidato = DIRECTORIO_DESTINO.resolve(sufijo);
            contador++;
        }
        return candidato;
    }

    private String extraerNombreBase(String nombre) {
        int dot = nombre.lastIndexOf('.');
        return (dot > 0) ? nombre.substring(0, dot) : nombre;
    }

    private Respuesta<String> crearRespuestaExitosa(String nombreFinal, Path ruta) {
        Mensaje<String> msg = new Mensaje<>();
        msg.setTipo(TipoMensaje.RESPONSE);
        msg.setAccion(Accion.FINALIZAR_STREAM);
        msg.setMetadata(crearMetadata());
        msg.setPayload("Archivo recibido correctamente: " + nombreFinal + " en " + ruta.toAbsolutePath());

        Respuesta<String> resp = new Respuesta<>();
        resp.setEstado(Estado.EXITO);
        resp.setMensaje(msg);
        return resp;
    }

    private Respuesta<?> crearError(String codigo, String detalle) {
        Respuesta<?> resp = new Respuesta<>();
        resp.setEstado(Estado.ERROR);
        resp.setError(new ErrorDetalle(codigo, detalle));
        return resp;
    }

    private Metadata crearMetadata() {
        Metadata m = new Metadata();
        m.setIdMensaje(UUID.randomUUID().toString());
        m.setTimestamp(LocalDateTime.now());
        return m;
    }

    private String resolverRemitente(Mensaje<?> mensaje) {
        if (mensaje.getMetadata() != null && mensaje.getMetadata().getClientId() != null) {
            return mensaje.getMetadata().getClientId();
        }
        return "desconocido";
    }

    private int puertoRemitente() {
        Integer p = ContextoSolicitud.obtenerPuertoRemitente();
        return p == null ? -1 : p;
    }
}
