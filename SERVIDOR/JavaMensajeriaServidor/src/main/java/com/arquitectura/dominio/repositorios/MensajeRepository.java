package com.arquitectura.dominio.repositorios;

import com.arquitectura.dominio.modelo.MensajeModel;

import java.time.LocalDateTime;
import java.util.List;

public interface MensajeRepository {

    void guardar(String mensajeId, String autor, String ipRemitente, String contenido,
                 String hashSha256, String contenidoCifrado, LocalDateTime fechaEnvio,
                 String servidorOrigen, String destinatario);

    boolean existePorId(String id);

    List<MensajeModel> listarTodos();

    List<MensajeModel> listarParaUsuario(String username);
}
