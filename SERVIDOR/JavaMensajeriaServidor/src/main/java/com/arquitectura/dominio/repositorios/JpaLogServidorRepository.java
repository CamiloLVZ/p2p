package com.arquitectura.dominio.repositorios;

import com.arquitectura.dominio.modelo.LogServidorModel;
import com.arquitectura.infraestructura.persistencia.HibernateManager;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;

import java.time.LocalDateTime;
import java.util.List;

public class JpaLogServidorRepository implements LogServidorRepository {

    @Override
    public void guardar(String nivel, String mensaje, String origen, String ipRemitente, LocalDateTime fechaEvento) {
        LogServidorModel entity = new LogServidorModel();
        entity.setNivel(nivel);
        entity.setMensaje(mensaje);
        entity.setOrigen(origen);
        entity.setIpRemitente(ipRemitente);
        entity.setFechaEvento(fechaEvento);

        EntityManager entityManager = HibernateManager.crearEntityManager();
        EntityTransaction transaction = entityManager.getTransaction();

        try {
            transaction.begin();
            entityManager.persist(entity);
            transaction.commit();
        } catch (Exception e) {
            if (transaction.isActive()) {
                transaction.rollback();
            }
            throw new IllegalStateException("No fue posible guardar el log del servidor en MySQL", e);
        } finally {
            entityManager.close();
        }
    }

    @Override
    public List<LogServidorModel> listarTodos() {
        EntityManager entityManager = HibernateManager.crearEntityManager();
        try {
            return entityManager
                    .createQuery("SELECT l FROM LogServidorModel l ORDER BY l.fechaEvento DESC", LogServidorModel.class)
                    .getResultList();
        } finally {
            entityManager.close();
        }
    }

    @Override
    public List<LogServidorModel> listarPaginado(int pagina, int tamanoPagina) {
        EntityManager em = HibernateManager.crearEntityManager();
        try {
            return em.createQuery("SELECT l FROM LogServidorModel l ORDER BY l.fechaEvento DESC", LogServidorModel.class)
                    .setFirstResult(pagina * tamanoPagina)
                    .setMaxResults(tamanoPagina)
                    .getResultList();
        } finally {
            em.close();
        }
    }

    @Override
    public long contarTotal() {
        EntityManager em = HibernateManager.crearEntityManager();
        try {
            return em.createQuery("SELECT COUNT(l) FROM LogServidorModel l", Long.class)
                    .getSingleResult();
        } finally {
            em.close();
        }
    }
}
