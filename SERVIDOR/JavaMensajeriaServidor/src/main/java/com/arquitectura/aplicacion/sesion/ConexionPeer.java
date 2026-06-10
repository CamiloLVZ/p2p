package com.arquitectura.aplicacion.sesion;

import java.net.Socket;
import java.time.LocalDateTime;

/**
 * Estado mutable en tiempo de ejecucion para la conexion con un peer.
 */
public class ConexionPeer {

    private final PeerConfig config;
    private final int maxIntentos;

    private volatile EstadoPeer estado;
    private int intentosReconexion;
    private LocalDateTime ultimaConexion;
    private Socket socketActivo;

    public ConexionPeer(PeerConfig config, int maxIntentos) {
        this.config = config;
        this.maxIntentos = maxIntentos;
        this.estado = EstadoPeer.HALF_OPEN;
        this.intentosReconexion = 0;
        this.ultimaConexion = null;
        this.socketActivo = null;
    }

    /** @return true si el peer esta en estado CLOSED (conexion activa). */
    public boolean estaConectado() {
        return estado == EstadoPeer.CLOSED;
    }

    /** Marca el peer como conectado exitosamente. */
    public synchronized void marcarConectado() {
        this.estado = EstadoPeer.CLOSED;
        this.intentosReconexion = 0;
        this.ultimaConexion = LocalDateTime.now();
    }

    /** Registra un fallo de conexion. El peer siempre queda en HALF_OPEN para reintento. */
    public synchronized void registrarFallo() {
        intentosReconexion++;
        // En arquitectura stateless (socket por mensaje) nunca bloqueamos permanentemente.
        // El peer siempre queda HALF_OPEN para que el proximo envio lo reintente.
        estado = EstadoPeer.HALF_OPEN;
    }

    /** Coloca el peer en HALF_OPEN para que el proximo envio intente reconectar. */
    public void marcarParaSondeo() {
        this.estado = EstadoPeer.HALF_OPEN;
    }

    public PeerConfig getConfig() {
        return config;
    }

    public EstadoPeer getEstado() {
        return estado;
    }

    public int getIntentosReconexion() {
        return intentosReconexion;
    }

    public LocalDateTime getUltimaConexion() {
        return ultimaConexion;
    }

    public Socket getSocketActivo() {
        return socketActivo;
    }

    public void setSocketActivo(Socket socketActivo) {
        this.socketActivo = socketActivo;
    }
}
