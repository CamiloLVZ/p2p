package com.arquitectura.infraestructura.transporte;

public class ProtocoloTransporteFactory {

    public static ProtocoloTransporte crear(String protocolo) {
        if ("TCP".equalsIgnoreCase(protocolo)) {
            return new TcpProtocoloTransporte();
        } else if ("UDP".equalsIgnoreCase(protocolo)) {
            return new UdpProtocoloTransporte();
        }
        throw new IllegalArgumentException("Protocolo no soportado");
    }
}
