package com.arquitectura.mensajeria.payload;

public class PayloadListarLogsRemoto {

    private String servidorId;
    private int pagina;
    private int tamanoPagina;

    public PayloadListarLogsRemoto() {}

    public String getServidorId() {
        return servidorId;
    }

    public void setServidorId(String servidorId) {
        this.servidorId = servidorId;
    }

    public int getPagina() {
        return pagina;
    }

    public void setPagina(int pagina) {
        this.pagina = pagina;
    }

    public int getTamanoPagina() {
        return tamanoPagina;
    }

    public void setTamanoPagina(int tamanoPagina) {
        this.tamanoPagina = tamanoPagina;
    }
}
