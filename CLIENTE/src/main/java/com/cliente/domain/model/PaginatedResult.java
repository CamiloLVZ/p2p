package com.cliente.domain.model;

import java.util.List;

public class PaginatedResult<T> {

    private List<T> registros;
    private int pagina;
    private int tamanoPagina;
    private long totalRegistros;
    private int totalPaginas;

    public PaginatedResult() {}

    public PaginatedResult(List<T> registros, int pagina, int tamanoPagina, long totalRegistros, int totalPaginas) {
        this.registros = registros;
        this.pagina = pagina;
        this.tamanoPagina = tamanoPagina;
        this.totalRegistros = totalRegistros;
        this.totalPaginas = totalPaginas;
    }

    public List<T> getRegistros() { return registros; }
    public void setRegistros(List<T> registros) { this.registros = registros; }

    public int getPagina() { return pagina; }
    public void setPagina(int pagina) { this.pagina = pagina; }

    public int getTamanoPagina() { return tamanoPagina; }
    public void setTamanoPagina(int tamanoPagina) { this.tamanoPagina = tamanoPagina; }

    public long getTotalRegistros() { return totalRegistros; }
    public void setTotalRegistros(long totalRegistros) { this.totalRegistros = totalRegistros; }

    public int getTotalPaginas() { return totalPaginas; }
    public void setTotalPaginas(int totalPaginas) { this.totalPaginas = totalPaginas; }
}
