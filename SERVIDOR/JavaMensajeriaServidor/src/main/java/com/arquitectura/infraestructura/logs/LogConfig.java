package com.arquitectura.infraestructura.logs;

import java.util.logging.ConsoleHandler;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

public final class LogConfig {

    private static volatile boolean configured = false;
    private static volatile boolean databaseHandlerConfigured = false;

    private LogConfig() {
    }

    public static void configureRootLogger() {
        if (configured) {
            return;
        }

        synchronized (LogConfig.class) {
            if (configured) {
                return;
            }

            Logger rootLogger = Logger.getLogger("");
            for (Handler handler : rootLogger.getHandlers()) {
                rootLogger.removeHandler(handler);
            }

            ConsoleHandler consoleHandler = new ConsoleHandler();
            consoleHandler.setLevel(Level.ALL);
            consoleHandler.setFormatter(new Formatter() {
                @Override
                public String format(LogRecord record) {
                    StringBuilder sb = new StringBuilder();
                    sb.append(String.format(
                            "[%1$tF %1$tT] [%2$-7s] [%3$s] %4$s%n",
                            record.getMillis(),
                            record.getLevel().getName(),
                            record.getLoggerName(),
                            formatMessage(record)
                    ));
                    if (record.getThrown() != null) {
                        java.io.StringWriter sw = new java.io.StringWriter();
                        record.getThrown().printStackTrace(new java.io.PrintWriter(sw));
                        sb.append(sw);
                    }
                    return sb.toString();
                }
            });

            rootLogger.addHandler(consoleHandler);
            rootLogger.setLevel(Level.INFO);
            configured = true;
        }
    }

    public static void configureDatabaseLogging() {
        if (databaseHandlerConfigured) {
            return;
        }

        synchronized (LogConfig.class) {
            if (databaseHandlerConfigured) {
                return;
            }

            Logger rootLogger = Logger.getLogger("");
            DatabaseLogHandler databaseLogHandler = new DatabaseLogHandler();
            databaseLogHandler.setLevel(Level.ALL);
            rootLogger.addHandler(databaseLogHandler);
            databaseHandlerConfigured = true;
        }
    }
}
