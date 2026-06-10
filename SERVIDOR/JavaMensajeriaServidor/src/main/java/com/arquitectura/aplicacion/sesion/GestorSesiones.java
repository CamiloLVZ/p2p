package com.arquitectura.aplicacion.sesion;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Gestor singleton de sesiones en memoria.
 *
 * <p>Controla:
 * - usernames unicos,
 * - limite maximo de sesiones,
 * - expiracion por inactividad,
 * - validacion del origen que puede operar una sesion.</p>
 */
public class GestorSesiones {

    private static final GestorSesiones INSTANCE = new GestorSesiones();

    private final Map<String, SesionCliente> sesionesPorUsername = new ConcurrentHashMap<>();

    /** Lock para proteger mutaciones del mapa. Lecturas puras no lo necesitan (ConcurrentHashMap). */
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    private volatile int maxSesiones = 10;
    private volatile Duration timeoutInactividad = Duration.ofMinutes(30);

    private GestorSesiones() {
    }

    public static GestorSesiones getInstance() {
        return INSTANCE;
    }

    public void configurar(int maxSesiones, Duration timeoutInactividad) {
        this.maxSesiones = maxSesiones;
        if (timeoutInactividad != null && !timeoutInactividad.isNegative() && !timeoutInactividad.isZero()) {
            this.timeoutInactividad = timeoutInactividad;
        }
    }

    /**
     * Registra una sesion nueva si el username esta libre y hay cupo.
     */
    public ResultadoRegistroSesion registrar(String username, String endpoint, String protocolo) {
        return registrar(username, extraerIp(endpoint), extraerPuerto(endpoint), protocolo);
    }

    public ResultadoRegistroSesion registrar(String username, String ipRemitente, int puertoRemitente, String protocolo) {
        lock.writeLock().lock();
        try {
            limpiarExpiradasInterno();

            String usernameNormalizado = normalizar(username);
            if (usernameNormalizado.isBlank()) {
                return ResultadoRegistroSesion.error("USERNAME_INVALIDO", "El username es obligatorio");
            }

            SesionCliente existente = sesionesPorUsername.get(usernameNormalizado);
            if (existente != null) {
                if (existente.mismaConexion(ipRemitente, puertoRemitente, protocolo)) {
                    existente.marcarActividad();
                    return ResultadoRegistroSesion.ok(existente, "Sesion ya existente para el usuario");
                }

                if (existente.mismoCanalLogico(ipRemitente, protocolo)) {
                    existente.actualizarOrigen(ipRemitente, puertoRemitente, protocolo);
                    return ResultadoRegistroSesion.reconexion(existente, "Sesion actualizada para nueva conexion del mismo cliente");
                }

                return ResultadoRegistroSesion.error("USERNAME_YA_REGISTRADO", "El username ya esta en uso");
            }

            if (sesionesPorUsername.size() >= maxSesiones) {
                return ResultadoRegistroSesion.error("MAX_SESIONES_ALCANZADO", "No hay cupo para nuevas sesiones");
            }

            SesionCliente sesion = new SesionCliente(usernameNormalizado, ipRemitente, puertoRemitente, protocolo);
            sesionesPorUsername.put(usernameNormalizado, sesion);
            return ResultadoRegistroSesion.ok(sesion, "Sesion registrada correctamente");
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean existeSesionActiva(String username) {
        if (username == null || username.isBlank()) return false;
        SesionCliente sesion = sesionesPorUsername.get(normalizar(username));
        if (sesion == null) return false;
        // Expiración lazy: si el único acceso es leer, no purgamos todo el mapa
        if (Duration.between(sesion.getUltimoAcceso(), Instant.now()).compareTo(timeoutInactividad) > 0) {
            sesionesPorUsername.remove(normalizar(username));
            return false;
        }
        return true;
    }

    public Optional<SesionCliente> obtener(String username) {
        if (username == null || username.isBlank()) return Optional.empty();
        SesionCliente sesion = sesionesPorUsername.get(normalizar(username));
        return Optional.ofNullable(sesion);
    }

    public ResultadoValidacionSesion validarSesion(String username, String ipRemitente, int puertoRemitente, String protocolo) {
        // La validación puede necesitar actualizar el puerto (UDP efímero) — write lock.
        lock.writeLock().lock();
        try {
            limpiarExpiradasInterno();
            if (username == null || username.isBlank()) {
                return ResultadoValidacionSesion.error("SESION_NO_REGISTRADA", "El usuario no fue informado");
            }

            SesionCliente sesion = sesionesPorUsername.get(normalizar(username));
            if (sesion == null) {
                return ResultadoValidacionSesion.error(
                        "SESION_NO_REGISTRADA",
                        "El usuario [" + username + "] no tiene una sesion activa. Primero debe registrarse."
                );
            }

            if (!sesion.aceptaOperacionDesde(ipRemitente, puertoRemitente, protocolo)) {
                return ResultadoValidacionSesion.error(
                        "ORIGEN_SESION_INVALIDO",
                        "La sesion activa de [" + sesion.getUsername() + "] no corresponde al origen actual "
                                + describirOrigen(ipRemitente, puertoRemitente, protocolo)
                );
            }

            sesion.marcarActividad();
            return ResultadoValidacionSesion.ok(sesion);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean eliminar(String username) {
        if (username == null || username.isBlank()) return false;
        lock.writeLock().lock();
        try {
            return sesionesPorUsername.remove(normalizar(username)) != null;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void marcarActividad(String username) {
        if (username == null || username.isBlank()) {
            return;
        }
        SesionCliente sesion = sesionesPorUsername.get(normalizar(username));
        if (sesion != null) {
            sesion.marcarActividad();
        }
    }

    public void limpiarExpiradas() {
        lock.writeLock().lock();
        try {
            limpiarExpiradasInterno();
        } finally {
            lock.writeLock().unlock();
        }
    }

    /** Versión interna — solo llamar con el write lock ya tomado. */
    private void limpiarExpiradasInterno() {
        Instant ahora = Instant.now();
        sesionesPorUsername.entrySet().removeIf(entry ->
                Duration.between(entry.getValue().getUltimoAcceso(), ahora).compareTo(timeoutInactividad) > 0
        );
    }

    public int sesionesActivas() {
        return sesionesPorUsername.size();
    }

    public java.util.Collection<SesionCliente> listarSesiones() {
        return java.util.Collections.unmodifiableCollection(sesionesPorUsername.values());
    }

    public void cerrarTodas() {
        lock.writeLock().lock();
        try {
            sesionesPorUsername.clear();
        } finally {
            lock.writeLock().unlock();
        }
    }

    private String normalizar(String username) {
        return username == null ? "" : username.trim().toLowerCase();
    }

    private String extraerIp(String endpoint) {
        if (endpoint == null || endpoint.isBlank() || "desconocido".equalsIgnoreCase(endpoint)) {
            return "desconocido";
        }

        int separador = endpoint.lastIndexOf(':');
        if (separador <= 0) {
            return endpoint;
        }

        return endpoint.substring(0, separador);
    }

    private int extraerPuerto(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            return -1;
        }

        int separador = endpoint.lastIndexOf(':');
        if (separador <= 0 || separador == endpoint.length() - 1) {
            return -1;
        }

        try {
            return Integer.parseInt(endpoint.substring(separador + 1));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private String describirOrigen(String ipRemitente, int puertoRemitente, String protocolo) {
        String endpoint = puertoRemitente > 0 ? ipRemitente + ":" + puertoRemitente : ipRemitente;
        return endpoint + " (" + protocolo + ")";
    }
}
