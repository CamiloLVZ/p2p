package com.arquitectura.dominio.repositorios;

import com.arquitectura.dominio.modelo.ArchivoRecibidoModel;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ArchivoRecibidoRepository {

    void guardar(String mensajeId, String remitente, String ipRemitente, String nombreArchivo, String extension,
                 String rutaArchivo, String hashSha256, String contenidoCifrado,
                 long tamano, LocalDateTime fechaRecepcion, String servidorOrigen);

    void guardar(String mensajeId, String remitente, String ipRemitente, String nombreArchivo, String extension,
                 String rutaArchivo, String hashSha256, String contenidoCifrado,
                 long tamano, LocalDateTime fechaRecepcion, String servidorOrigen, String destinatario);

    boolean existePorId(String id);

    List<ArchivoRecibidoModel> listarTodos();

    /** Retorna archivos donde destinatario IS NULL (broadcast) OR destinatario = username */
    List<ArchivoRecibidoModel> listarParaUsuario(String username);

    Optional<ArchivoRecibidoModel> buscarPorId(String id);
}
