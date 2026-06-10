package com.arquitectura.aplicacion.sesion;

/**
 * Estado del circuito de conexion con un servidor peer.
 *
 * <ul>
 *   <li>CLOSED   — conexion activa, operacion normal.</li>
 *   <li>OPEN     — maximo de reintentos agotado, sin polling.</li>
 *   <li>HALF_OPEN — en sondeo: el proximo envio intentara reconectar.</li>
 * </ul>
 */
public enum EstadoPeer {
    CLOSED,
    OPEN,
    HALF_OPEN
}
