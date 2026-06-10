package com.arquitectura.aplicacion;

import com.arquitectura.comun.dto.PaqueteDatos;
import com.arquitectura.infraestructura.transporte.ProtocoloTransporte;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;

public class RespuestaSender {

    public void enviar(PaqueteDatos paquete, String respuestaJson,
                       ProtocoloTransporte transporte) throws IOException {

        if (paquete.getSocket() != null) {
            BufferedWriter writer = new BufferedWriter(
                    new OutputStreamWriter(paquete.getSocket().getOutputStream())
            );
            writer.write(respuestaJson);
            writer.newLine();
            writer.flush();
            paquete.getSocket().close();
        } else {
            transporte.enviar(
                    respuestaJson.getBytes(),
                    paquete.getHostOrigen(),
                    paquete.getPuertoOrigen()
            );
        }
    }
}