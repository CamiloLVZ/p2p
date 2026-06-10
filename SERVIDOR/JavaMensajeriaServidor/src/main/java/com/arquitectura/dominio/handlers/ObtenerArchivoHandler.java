package com.arquitectura.dominio.handlers;

import com.arquitectura.aplicacion.ContextoSolicitud;
import com.arquitectura.aplicacion.router.Handler;
import com.arquitectura.aplicacion.sesion.GestorSesiones;
import com.arquitectura.aplicacion.sesion.ResultadoValidacionSesion;
import com.arquitectura.aplicacion.transferencia.GestorDescargasActivas;
import com.arquitectura.dominio.modelo.ArchivoRecibidoModel;
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
import com.arquitectura.mensajeria.payload.PayloadIniciarDescarga;
import com.arquitectura.mensajeria.payload.PayloadSolicitarStream;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Maneja SOLICITAR_STREAM — el cliente pide descargar un archivo.
 *
 * Flujo:
 * 1. Valida sesión.
 * 2. Busca el archivo por ID en la base de datos.
 * 3. Verifica que el archivo exista en disco.
 * 4. Genera un transferId para esta sesión de descarga.
 * 5. Registra la descarga en GestorDescargasActivas (el emisor la usará).
 * 6. Responde con PayloadIniciarDescarga: metadatos + hash del archivo.
 *
 * Luego el cliente abre una segunda conexión con señal 0x03 para pedir los chunks.
 * El servidor la detecta en el transporte y llama a StreamEmisorTcp/Udp.
 */
public class ObtenerArchivoHandler implements Handler<PayloadSolicitarStream> {

    private static final Logger LOGGER = Logger.getLogger(ObtenerArchivoHandler.class.getName());

    /** Tamaño de chunk para descarga TCP: 2 MB. */
    private static final int CHUNK_SIZE_TCP = 2 * 1024 * 1024;

    /** Tamaño de chunk para descarga UDP: 60 KB. */
    private static final int CHUNK_SIZE_UDP = 60_000;

    private final ArchivoRecibidoRepository repositorio = new JpaArchivoRecibidoRepository();
    private final GestorSesiones gestorSesiones = GestorSesiones.getInstance();
    private final GestorDescargasActivas gestorDescargas = GestorDescargasActivas.getInstance();

    @Override
    public Respuesta<?> handle(Mensaje<PayloadSolicitarStream> mensaje) {
        PayloadSolicitarStream payload = mensaje.getPayload();
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

        String archivoId = payload.getArchivoId();
        if (archivoId == null || archivoId.isBlank()) {
            return crearError("ARCHIVO_ID_REQUERIDO", "El campo archivoId es obligatorio.");
        }

        Optional<ArchivoRecibidoModel> encontrado = repositorio.buscarPorId(archivoId);
        if (encontrado.isEmpty()) {
            return crearError("ARCHIVO_NO_ENCONTRADO", "No existe un archivo con id: " + archivoId);
        }

        ArchivoRecibidoModel archivo = encontrado.get();
        Path rutaArchivo = Path.of(archivo.getRutaArchivo());

        if (!Files.exists(rutaArchivo)) {
            LOGGER.warning(() -> "Archivo en DB pero no en disco: " + rutaArchivo);
            return crearError("ARCHIVO_NO_EN_DISCO",
                    "El archivo fue registrado pero no se encuentra en el servidor.");
        }

        // Determinar tamaño de chunk según protocolo
        boolean esTcp = "TCP".equalsIgnoreCase(ContextoSolicitud.obtenerProtocolo());
        int chunkSize = esTcp ? CHUNK_SIZE_TCP : CHUNK_SIZE_UDP;
        long tamano = archivo.getTamano();
        long totalChunks = Math.max(1L, (tamano + chunkSize - 1) / chunkSize);

        // Calcular hash si no está almacenado (compatibilidad con archivos viejos)
        String hash = archivo.getHashSha256();
        if (hash == null || hash.isBlank()) {
            try {
                hash = CryptoUtil.sha256Base64(Files.readAllBytes(rutaArchivo));
            } catch (Exception e) {
                LOGGER.warning(() -> "No se pudo calcular hash de " + rutaArchivo + ": " + e.getMessage());
                hash = "";
            }
        }

        String transferId = UUID.randomUUID().toString();

        // Registrar la descarga para que el emisor la encuentre cuando llegue la señal 0x03
        gestorDescargas.registrar(transferId, rutaArchivo, chunkSize);

        LOGGER.info(() -> "Descarga autorizada: transferId=" + transferId
                + " | archivo=" + archivo.getNombreArchivo()
                + " | tamano=" + tamano
                + " | chunks=" + totalChunks
                + " | cliente=" + remitente);

        PayloadIniciarDescarga respPayload = new PayloadIniciarDescarga(
                transferId,
                archivo.getNombreArchivo(),
                archivo.getExtension(),
                tamano,
                totalChunks,
                chunkSize,
                hash
        );

        Mensaje<PayloadIniciarDescarga> msgResp = new Mensaje<>();
        msgResp.setTipo(TipoMensaje.RESPONSE);
        msgResp.setAccion(Accion.INICIAR_DESCARGA);
        msgResp.setMetadata(crearMetadata());
        msgResp.setPayload(respPayload);

        Respuesta<PayloadIniciarDescarga> respuesta = new Respuesta<>();
        respuesta.setEstado(Estado.EXITO);
        respuesta.setMensaje(msgResp);
        return respuesta;
    }

    @Override
    public Class<PayloadSolicitarStream> getPayloadClass() {
        return PayloadSolicitarStream.class;
    }

    private Respuesta<?> crearError(String codigo, String detalle) {
        Respuesta<?> r = new Respuesta<>();
        r.setEstado(Estado.ERROR);
        r.setError(new ErrorDetalle(codigo, detalle));
        return r;
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
