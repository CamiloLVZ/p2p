package com.arquitectura.infraestructura.logs;

import com.arquitectura.aplicacion.ContextoSolicitud;
import com.arquitectura.dominio.repositorios.JpaLogServidorRepository;
import com.arquitectura.dominio.repositorios.LogServidorRepository;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.ErrorManager;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

public class DatabaseLogHandler extends Handler {

    private static final ThreadLocal<Boolean> EN_PUBLICACION = ThreadLocal.withInitial(() -> false);

    private final LogServidorRepository logServidorRepository = new JpaLogServidorRepository();

    /**
     * Hilo dedicado para persistir logs — el worker que genera el log NO espera el INSERT.
     * Un solo hilo es suficiente: los logs son secuenciales y no necesitan paralelismo.
     */
    private final ExecutorService logExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "db-log-writer");
        t.setDaemon(true);
        return t;
    });

    public DatabaseLogHandler() {
        setFormatter(new Formatter() {
            @Override
            public String format(LogRecord record) {
                StringBuilder builder = new StringBuilder(formatMessage(record));
                if (record.getThrown() != null) {
                    StringWriter stringWriter = new StringWriter();
                    record.getThrown().printStackTrace(new PrintWriter(stringWriter));
                    builder.append(System.lineSeparator()).append(stringWriter);
                }
                return builder.toString();
            }
        });
    }

    @Override
    public void publish(LogRecord record) {
        if (!isLoggable(record) || EN_PUBLICACION.get()) {
            return;
        }

        // Capturamos todo lo necesario AHORA (en el hilo del caller) antes de ir async,
        // porque LogRecord y ContextoSolicitud son thread-local / mutables.
        final String nivel      = record.getLevel().getName();
        final String mensaje    = getFormatter().format(record);
        final String origen     = record.getLoggerName();
        final String ip         = ContextoSolicitud.obtenerIpRemitente();
        final LocalDateTime ts  = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(record.getMillis()), ZoneId.systemDefault());

        logExecutor.submit(() -> {
            if (EN_PUBLICACION.get()) return;
            try {
                EN_PUBLICACION.set(true);
                logServidorRepository.guardar(nivel, mensaje, origen, ip, ts);
            } catch (Exception e) {
                reportError("No fue posible persistir el log en base de datos", e, ErrorManager.WRITE_FAILURE);
            } finally {
                EN_PUBLICACION.remove();
            }
        });
    }

    @Override
    public void flush() { }

    @Override
    public void close() {
        logExecutor.shutdown();
    }
}

