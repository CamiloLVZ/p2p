package com.arquitectura.mensajeria.payload;


public class PayloadObtenerArchivo {

    private String fileId;
    private OpcionesArchivo opciones;

    public PayloadObtenerArchivo() {}

    public PayloadObtenerArchivo(String fileId, OpcionesArchivo opciones) {
        this.fileId = fileId;
        this.opciones = opciones;
    }

    public String getFileId() {
        return fileId;
    }

    public void setFileId(String fileId) {
        this.fileId = fileId;
    }

    public OpcionesArchivo getOpciones() {
        return opciones;
    }

    public void setOpciones(OpcionesArchivo opciones) {
        this.opciones = opciones;
    }
}