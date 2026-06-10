package com.arquitectura.aplicacion.concurrencia;

import com.arquitectura.aplicacion.ProcesadorMensajes;
import com.arquitectura.aplicacion.RespuestaSender;
import com.arquitectura.comun.dto.PaqueteDatos;
import com.arquitectura.infraestructura.transporte.ProtocoloTransporte;
import com.arquitectura.infraestructura.transporte.TcpProtocoloTransporte;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Tarea reutilizable que atiende 1 cliente/solicitud.
 *
 * <p>Esta clase se reutiliza mediante Object Pool: en vez de crear un Runnable nuevo
 * por cada cliente, se "presta" una instancia, se ejecuta y luego se devuelve al pool.</p>
 */
public class AtencionClienteTask implements Runnable {

    private static final Logger LOGGER = Logger.getLogger(AtencionClienteTask.class.getName());

    private PaqueteDatos paquete;
    private ProcesadorMensajes procesador;
    private RespuestaSender sender;
    private ProtocoloTransporte transporte;

    /**
     * Callback para devolver esta tarea al pool al finalizar.
     */
    private Runnable onFinish;

    /**
     * Carga en la tarea todos los datos de la solicitud actual.
     */
    public void preparar(PaqueteDatos paquete,
                         ProcesadorMensajes procesador,
                         RespuestaSender sender,
                         ProtocoloTransporte transporte,
                         Runnable onFinish) {
        this.paquete = paquete;
        this.procesador = procesador;
        this.sender = sender;
        this.transporte = transporte;
        this.onFinish = onFinish;
    }

    @Override
    public void run() {
        String threadName = Thread.currentThread().getName();
        LOGGER.fine(() -> "[" + threadName + "] Worker iniciando atencion de cliente");
        try {
            // Para TCP, el paquete llega con socket pero sin datos (el main solo hizo accept).
            // La lectura real ocurre acá, en el worker thread.
            PaqueteDatos paqueteReal = resolverPaquete();
            if (paqueteReal == null) {
                LOGGER.fine(() -> "[" + threadName + "] Paquete nulo (streaming o conexion vacia). Liberando worker.");
                return;
            }

            LOGGER.fine(() -> "[" + threadName + "] Procesando mensaje JSON del cliente");
            String respuesta = procesador.procesar(paqueteReal);
            sender.enviar(paqueteReal, respuesta, transporte);
            LOGGER.fine(() -> "[" + threadName + "] Respuesta enviada correctamente");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "[" + threadName + "] Error atendiendo cliente", e);
        } finally {
            // IMPORTANTE: capturar el callback ANTES de limpiar estado,
            // porque limpiarEstado() anula todas las referencias (incluido onFinish).
            Runnable retornoAlPool = onFinish;

            limpiarEstado();

            if (retornoAlPool != null) {
                LOGGER.fine(() -> "[" + threadName + "] Devolviendo tarea al pool");
                retornoAlPool.run();
            } else {
                LOGGER.warning(() -> "[" + threadName + "] onFinish era null — tarea NO devuelta al pool!");
            }
        }
    }

    /**
     * Si el transporte es TCP y el paquete no tiene datos todavía,
     * delega la lectura al transporte para que resuelva el tipo de mensaje
     * (JSON, upload o download) — todo en este hilo worker.
     *
     * Cuando el transporte despacha streaming (upload/download), la propiedad
     * del socket se transfiere al hilo de streaming. En ese caso se anula
     * {@code this.paquete} para que {@link #limpiarEstado()} NO cierre el socket.
     */
    private PaqueteDatos resolverPaquete() throws IOException {
        if (transporte instanceof TcpProtocoloTransporte tcp && paquete.getData() == null) {
            PaqueteDatos resultado = tcp.leerDesdSocket(paquete.getSocket());
            if (resultado == null) {
                // Socket ownership transferido al streaming handler (o conexión vacía/cerrada).
                // Anular paquete para que limpiarEstado() no cierre el socket.
                paquete = null;
            }
            return resultado;
        }
        return paquete;
    }

    /**
     * Limpia referencias para que la instancia quede lista para la próxima solicitud.
     */
    private void limpiarEstado() {
        if (paquete != null && paquete.getSocket() != null && !paquete.getSocket().isClosed()) {
            try {
                paquete.getSocket().close();
            } catch (IOException e) {
                LOGGER.log(Level.FINE, "No se pudo cerrar el socket del cliente", e);
            }
        }

        paquete = null;
        procesador = null;
        sender = null;
        transporte = null;
        onFinish = null;
    }
}
