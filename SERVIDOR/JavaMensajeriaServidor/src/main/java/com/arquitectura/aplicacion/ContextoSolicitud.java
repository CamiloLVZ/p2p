package com.arquitectura.aplicacion;

public final class ContextoSolicitud {

    private static final ThreadLocal<String> IP_REMITENTE = new ThreadLocal<>();
    private static final ThreadLocal<Integer> PUERTO_REMITENTE = new ThreadLocal<>();
    private static final ThreadLocal<String> PROTOCOLO = new ThreadLocal<>();

    private ContextoSolicitud() {
    }

    public static void establecerOrigen(String ipRemitente, int puertoRemitente, String protocolo) {
        IP_REMITENTE.set(ipRemitente);
        PUERTO_REMITENTE.set(puertoRemitente);
        PROTOCOLO.set(protocolo);
    }

    public static String obtenerIpRemitente() {
        return IP_REMITENTE.get();
    }

    public static Integer obtenerPuertoRemitente() {
        return PUERTO_REMITENTE.get();
    }

    public static String obtenerProtocolo() {
        return PROTOCOLO.get();
    }

    public static String obtenerEndpointRemitente() {
        String ip = obtenerIpRemitente();
        Integer puerto = obtenerPuertoRemitente();
        if (ip == null) {
            return "desconocido";
        }
        if (puerto == null || puerto <= 0) {
            return ip;
        }
        return ip + ":" + puerto;
    }

    public static void limpiar() {
        IP_REMITENTE.remove();
        PUERTO_REMITENTE.remove();
        PROTOCOLO.remove();
    }
}
