package com.arquitectura.infraestructura.seguridad;

import java.util.Properties;

public final class CryptoConfig {

    private static String aesKey;

    private CryptoConfig() {
    }

    public static void configurar(Properties properties) {
        aesKey = properties.getProperty("security.aes.key");
        if (aesKey == null || aesKey.isBlank()) {
            throw new IllegalStateException("Falta la propiedad requerida: security.aes.key");
        }
    }

    public static String getAesKey() {
        if (aesKey == null || aesKey.isBlank()) {
            throw new IllegalStateException("La configuracion AES no ha sido inicializada");
        }

        return aesKey;
    }
}
