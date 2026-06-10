package com.arquitectura.infraestructura.transporte;

import com.arquitectura.comun.dto.PaqueteDatos;

public interface ProtocoloTransporte {

    void iniciar(int port);
    void enviar(byte[] data, String host, int port);
    PaqueteDatos recibir();
    void detener();
    String getNombre();
}