package com.mediconecta.seguridad;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Hash de contrasenas con PBKDF2-HMAC-SHA256 y sal aleatoria por usuario.
 *
 * <p>Formato guardado: {@code pbkdf2$<iteraciones>$<sal base64>$<hash base64>}.
 * Guardar las iteraciones junto al hash permite subirlas en el futuro sin
 * invalidar las contrasenas existentes.</p>
 *
 * <p>Usa solo la JDK: no agrega dependencias al WAR.</p>
 */
public final class HashDeClaves {

    private static final String ALGORITMO = "PBKDF2WithHmacSHA256";

    private static final String PREFIJO = "pbkdf2";

    private static final int ITERACIONES = 120_000;

    private static final int BYTES_SAL = 16;

    private static final int BITS_HASH = 256;

    private static final SecureRandom AZAR = new SecureRandom();

    private HashDeClaves() {
    }

    /** Calcula el hash a guardar para una contrasena nueva. */
    public static String calcular(String clave) {
        if (clave == null || clave.isEmpty()) {
            throw new IllegalArgumentException("La contrasena no puede estar vacia.");
        }
        byte[] sal = new byte[BYTES_SAL];
        AZAR.nextBytes(sal);
        byte[] hash = derivar(clave.toCharArray(), sal, ITERACIONES);
        Base64.Encoder b64 = Base64.getEncoder();
        return PREFIJO + "$" + ITERACIONES + "$" + b64.encodeToString(sal) + "$" + b64.encodeToString(hash);
    }

    /**
     * Compara una contrasena con un hash guardado, en tiempo constante.
     *
     * @return false si no coincide o si el hash guardado esta mal formado
     */
    public static boolean coincide(String clave, String guardado) {
        if (clave == null || guardado == null) {
            return false;
        }
        String[] partes = guardado.split("\\$");
        if (partes.length != 4 || !PREFIJO.equals(partes[0])) {
            return false;
        }
        try {
            int iteraciones = Integer.parseInt(partes[1]);
            byte[] sal = Base64.getDecoder().decode(partes[2]);
            byte[] esperado = Base64.getDecoder().decode(partes[3]);
            byte[] calculado = derivar(clave.toCharArray(), sal, iteraciones);
            return MessageDigest.isEqual(esperado, calculado);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static byte[] derivar(char[] clave, byte[] sal, int iteraciones) {
        PBEKeySpec especificacion = new PBEKeySpec(clave, sal, iteraciones, BITS_HASH);
        try {
            return SecretKeyFactory.getInstance(ALGORITMO).generateSecret(especificacion).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("La JVM no soporta " + ALGORITMO, e);
        } finally {
            especificacion.clearPassword();
        }
    }
}
