package com.arquitectura.aplicacion.sesion;

/**
 * Resultado de intentar registrar una sesion.
 */
public record ResultadoRegistroSesion(boolean exito,
                                      String codigoError,
                                      String mensaje,
                                      SesionCliente sesion,
                                      boolean reconexion) {

    public static ResultadoRegistroSesion ok(SesionCliente sesion, String mensaje) {
        return new ResultadoRegistroSesion(true, null, mensaje, sesion, false);
    }

    public static ResultadoRegistroSesion reconexion(SesionCliente sesion, String mensaje) {
        return new ResultadoRegistroSesion(true, null, mensaje, sesion, true);
    }

    public static ResultadoRegistroSesion error(String codigoError, String mensaje) {
        return new ResultadoRegistroSesion(false, codigoError, mensaje, null, false);
    }
}
