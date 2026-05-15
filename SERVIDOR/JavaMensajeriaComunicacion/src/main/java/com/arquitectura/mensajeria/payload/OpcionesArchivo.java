package com.arquitectura.mensajeria.payload;

public class OpcionesArchivo {

    private boolean incluirHash;
    private boolean encriptado;

    public OpcionesArchivo() {}

    public OpcionesArchivo(boolean incluirHash, boolean encriptado) {
        this.incluirHash = incluirHash;
        this.encriptado = encriptado;
    }

    public boolean isIncluirHash() {
        return incluirHash;
    }

    public void setIncluirHash(boolean incluirHash) {
        this.incluirHash = incluirHash;
    }

    public boolean isEncriptado() {
        return encriptado;
    }

    public void setEncriptado(boolean encriptado) {
        this.encriptado = encriptado;
    }
}