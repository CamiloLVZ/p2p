package com.arquitectura.mensajeria;

import com.arquitectura.mensajeria.enums.*;

public class Mensaje<T> {

    private TipoMensaje tipo;
    private Accion accion;
    private Metadata metadata;
    private T payload;

    public Mensaje() {}

    public Mensaje(TipoMensaje tipo, Accion accion, Metadata metadata, T payload) {
        this.tipo = tipo;
        this.accion = accion;
        this.metadata = metadata;
        this.payload = payload;
    }

    public TipoMensaje getTipo() {
        return tipo;
    }

    public void setTipo(TipoMensaje tipo) {
        this.tipo = tipo;
    }

    public Accion getAccion() {
        return accion;
    }

    public void setAccion(Accion accion) {
        this.accion = accion;
    }

    public Metadata getMetadata() {
        return metadata;
    }

    public void setMetadata(Metadata metadata) {
        this.metadata = metadata;
    }

    public T getPayload() {
        return payload;
    }

    public void setPayload(T payload) {
        this.payload = payload;
    }
}