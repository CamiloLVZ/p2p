package com.arquitectura.mensajeria;

import com.arquitectura.mensajeria.enums.*;

public class Respuesta<T> {

    private Mensaje<T> mensaje;
    private Estado estado;
    private ErrorDetalle error;

    public Respuesta() {}

    public Respuesta(Mensaje<T> mensaje, Estado estado, ErrorDetalle error) {
        this.mensaje = mensaje;
        this.estado = estado;
        this.error = error;
    }

    public Mensaje<T> getMensaje() {
        return mensaje;
    }

    public void setMensaje(Mensaje<T> mensaje) {
        this.mensaje = mensaje;
    }

    public Estado getEstado() {
        return estado;
    }

    public void setEstado(Estado estado) {
        this.estado = estado;
    }

    public ErrorDetalle getError() {
        return error;
    }

    public void setError(ErrorDetalle error) {
        this.error = error;
    }
}