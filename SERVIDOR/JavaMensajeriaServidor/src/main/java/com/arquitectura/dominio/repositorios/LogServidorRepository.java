package com.arquitectura.dominio.repositorios;

import com.arquitectura.dominio.modelo.LogServidorModel;

import java.time.LocalDateTime;
import java.util.List;

public interface LogServidorRepository {

    void guardar(String nivel, String mensaje, String origen, String ipRemitente, LocalDateTime fechaEvento);

    List<LogServidorModel> listarTodos();

    List<LogServidorModel> listarPaginado(int pagina, int tamanoPagina);

    long contarTotal();
}
