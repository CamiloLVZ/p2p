package com.arquitectura.infraestructura.concurrencia;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Pool de objetos reutilizables (Object Pool).
 *
 * <p>Su responsabilidad es mantener una cantidad fija de instancias pre-creadas
 * para evitar crear/destruir objetos en cada solicitud.</p>
 *
 * @param <T> tipo de objeto a reutilizar.
 */
public class ObjectPool<T> {

    private static final Logger LOGGER = Logger.getLogger(ObjectPool.class.getName());

    private final BlockingQueue<T> disponibles;
    private final int tamanoMaximo;

    /**
     * Crea el pool y llena todas sus posiciones con objetos listos para usar.
     *
     * @param tamanoMaximo cantidad máxima de objetos disponibles simultáneamente.
     * @param creador      función que crea cada objeto inicial del pool.
     */
    public ObjectPool(int tamanoMaximo, Supplier<T> creador) {
        if (tamanoMaximo <= 0) {
            throw new IllegalArgumentException("El tamaño del pool debe ser mayor a 0");
        }

        this.tamanoMaximo = tamanoMaximo;
        this.disponibles = new ArrayBlockingQueue<>(tamanoMaximo);

        for (int i = 0; i < tamanoMaximo; i++) {
            disponibles.add(creador.get());
        }

        LOGGER.info(() -> "ObjectPool creado con " + tamanoMaximo + " instancias");
    }

    /**
     * Toma (presta) un objeto del pool, esperando indefinidamente.
     *
     * <p>Si no hay objetos disponibles, espera hasta que otro hilo lo libere.
     * Esto permite limitar de forma natural la concurrencia máxima.</p>
     */
    public T tomar() throws InterruptedException {
        T obj = disponibles.take();
        LOGGER.fine(() -> "Pool: objeto tomado. Disponibles: " + disponibles.size() + "/" + tamanoMaximo);
        return obj;
    }

    /**
     * Toma (presta) un objeto del pool, esperando como máximo el tiempo indicado.
     *
     * @return el objeto prestado, o {@code null} si no hubo disponible en el plazo.
     */
    public T tomar(long timeout, TimeUnit unidad) throws InterruptedException {
        T obj = disponibles.poll(timeout, unidad);
        if (obj != null) {
            LOGGER.fine(() -> "Pool: objeto tomado (con timeout). Disponibles: " + disponibles.size() + "/" + tamanoMaximo);
        } else {
            LOGGER.warning(() -> "Pool: timeout agotado, no hay objetos disponibles. Disponibles: " + disponibles.size() + "/" + tamanoMaximo);
        }
        return obj;
    }

    /**
     * Devuelve un objeto al pool para que pueda reutilizarse.
     */
    public void devolver(T objeto) {
        if (objeto == null) {
            return;
        }
        boolean devuelto = disponibles.offer(objeto);
        if (devuelto) {
            LOGGER.fine(() -> "Pool: objeto devuelto. Disponibles: " + disponibles.size() + "/" + tamanoMaximo);
        } else {
            LOGGER.warning("Pool: no se pudo devolver el objeto (pool lleno). Posible doble devolucion.");
        }
    }
}
