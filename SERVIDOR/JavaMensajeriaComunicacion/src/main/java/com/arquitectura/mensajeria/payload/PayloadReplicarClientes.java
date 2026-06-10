package com.arquitectura.mensajeria.payload;

import java.util.List;

public class PayloadReplicarClientes {

    private String servidorOrigen;
    private List<PayloadClienteRemoto> clientes;

    public PayloadReplicarClientes() {}

    public String getServidorOrigen() {
        return servidorOrigen;
    }

    public void setServidorOrigen(String servidorOrigen) {
        this.servidorOrigen = servidorOrigen;
    }

    public List<PayloadClienteRemoto> getClientes() {
        return clientes;
    }

    public void setClientes(List<PayloadClienteRemoto> clientes) {
        this.clientes = clientes;
    }
}
