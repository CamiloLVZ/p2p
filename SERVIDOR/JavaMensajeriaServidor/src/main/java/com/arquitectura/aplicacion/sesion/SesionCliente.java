package com.arquitectura.aplicacion.sesion;

import java.time.Instant;

/**
 * Representa una sesion activa en memoria.
 */
public class SesionCliente {

    private final String username;
    private final Instant creadoEn;
    private volatile String ipRemitente;
    private volatile int puertoRemitente;
    private volatile String protocolo;
    private volatile Instant ultimoAcceso;

    public SesionCliente(String username, String ipRemitente, int puertoRemitente, String protocolo) {
        this.username = username;
        this.ipRemitente = ipRemitente;
        this.puertoRemitente = puertoRemitente;
        this.protocolo = protocolo;
        this.creadoEn = Instant.now();
        this.ultimoAcceso = this.creadoEn;
    }

    public String getUsername() {
        return username;
    }

    public String getIpRemitente() {
        return ipRemitente;
    }

    public int getPuertoRemitente() {
        return puertoRemitente;
    }

    public String getProtocolo() {
        return protocolo;
    }

    public String getEndpoint() {
        if (puertoRemitente <= 0) {
            return ipRemitente;
        }
        return ipRemitente + ":" + puertoRemitente;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }

    public Instant getUltimoAcceso() {
        return ultimoAcceso;
    }

    public synchronized void actualizarOrigen(String ipRemitente, int puertoRemitente, String protocolo) {
        this.ipRemitente = ipRemitente;
        this.puertoRemitente = puertoRemitente;
        this.protocolo = protocolo;
        marcarActividad();
    }

    public boolean mismoCanalLogico(String ipRemitente, String protocolo) {
        return this.ipRemitente != null
                && this.ipRemitente.equals(ipRemitente)
                && this.protocolo != null
                && this.protocolo.equalsIgnoreCase(protocolo);
    }

    public boolean mismaConexion(String ipRemitente, int puertoRemitente, String protocolo) {
        return mismoCanalLogico(ipRemitente, protocolo) && this.puertoRemitente == puertoRemitente;
    }

    public boolean aceptaOperacionDesde(String ipRemitente, int puertoRemitente, String protocolo) {
        if (!mismoCanalLogico(ipRemitente, protocolo)) {
            return false;
        }

        // UDP: los sockets son efímeros — el puerto cambia en cada datagrama.
        // Basta verificar IP + protocolo. Actualizamos el puerto para mantener coherencia.
        if ("UDP".equalsIgnoreCase(protocolo)) {
            if (this.puertoRemitente != puertoRemitente) {
                this.puertoRemitente = puertoRemitente;
            }
            return true;
        }

        // TCP: conexión persistente — el puerto es estable, no lo validamos
        // (cada request abre una conexión nueva con puerto efímero distinto).
        return true;
    }

    public void marcarActividad() {
        this.ultimoAcceso = Instant.now();
    }
}
