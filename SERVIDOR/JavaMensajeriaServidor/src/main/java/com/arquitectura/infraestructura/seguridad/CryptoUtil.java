package com.arquitectura.infraestructura.seguridad;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;

public final class CryptoUtil {

    private static final String AES = "AES";
    private static final String AES_TRANSFORMATION = "AES/ECB/PKCS5Padding";

    private CryptoUtil() {
    }

    public static String sha256Base64(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return Base64.getEncoder().encodeToString(digest.digest(data));
        } catch (Exception e) {
            throw new IllegalStateException("No fue posible generar el hash SHA-256", e);
        }
    }

    public static String aesEncryptBase64(byte[] data) {
        try {
            Cipher cipher = Cipher.getInstance(AES_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, buildKey());
            return Base64.getEncoder().encodeToString(cipher.doFinal(data));
        } catch (Exception e) {
            throw new IllegalStateException("No fue posible cifrar el contenido con AES", e);
        }
    }

    public static byte[] aesDecryptBase64(String base64Cifrado) {
        try {
            byte[] cipherBytes = Base64.getDecoder().decode(base64Cifrado);
            Cipher cipher = Cipher.getInstance(AES_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, buildKey());
            return cipher.doFinal(cipherBytes);
        } catch (Exception e) {
            throw new IllegalStateException("No fue posible descifrar el contenido con AES", e);
        }
    }

    private static SecretKeySpec buildKey() {
        byte[] keyBytes = CryptoConfig.getAesKey().getBytes(StandardCharsets.UTF_8);
        byte[] normalized = Arrays.copyOf(keyBytes, 16);
        return new SecretKeySpec(normalized, AES);
    }
}
