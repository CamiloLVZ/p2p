package com.arquitectura.infraestructura.persistencia;

import java.util.Properties;

public final class ConexionMySql {

    private ConexionMySql() {
    }

    public static void configurar(Properties properties) {
        HibernateManager.inicializar(properties);
    }

    public static void cerrar() {
        HibernateManager.cerrar();
    }
}
