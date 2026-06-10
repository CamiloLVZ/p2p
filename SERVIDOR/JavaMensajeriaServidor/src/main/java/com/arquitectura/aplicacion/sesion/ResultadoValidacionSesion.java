package com.arquitectura.aplicacion.sesion;

/**
 * Resultado de validar que una operacion pertenezca a una sesion vigente.
 */
public record ResultadoValidacionSesion(boolean exito, String codigoError, String mensaje, SesionCliente sesion) {

    public static ResultadoValidacionSesion ok(SesionCliente sesion) {
        return new ResultadoValidacionSesion(true, null, null, sesion);
    }

    public static ResultadoValidacionSesion error(String codigoError, String mensaje) {
        return new ResultadoValidacionSesion(false, codigoError, mensaje, null);
    }
}
