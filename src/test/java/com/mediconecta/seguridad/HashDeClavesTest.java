package com.mediconecta.seguridad;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HashDeClavesTest {

    @Test
    void laClaveCorrectaCoincide() {
        String hash = HashDeClaves.calcular("Demo.2026");
        assertTrue(HashDeClaves.coincide("Demo.2026", hash));
    }

    @Test
    void unaClaveIncorrectaNoCoincide() {
        String hash = HashDeClaves.calcular("Demo.2026");
        assertFalse(HashDeClaves.coincide("demo.2026", hash));
        assertFalse(HashDeClaves.coincide("", hash));
        assertFalse(HashDeClaves.coincide(null, hash));
    }

    @Test
    void laClaveNoSeGuardaEnClaroYCadaHashTieneSuPropiaSal() {
        String primero = HashDeClaves.calcular("Demo.2026");
        String segundo = HashDeClaves.calcular("Demo.2026");
        assertFalse(primero.contains("Demo.2026"));
        assertNotEquals(primero, segundo);
        assertTrue(primero.startsWith("pbkdf2$"));
    }

    @Test
    void unHashMalFormadoNoCoincideNiExplota() {
        assertFalse(HashDeClaves.coincide("x", "no-es-un-hash"));
        assertFalse(HashDeClaves.coincide("x", "pbkdf2$abc$def$ghi"));
        assertFalse(HashDeClaves.coincide("x", null));
    }

    @Test
    void noSeAceptaUnaClaveVacia() {
        assertThrows(IllegalArgumentException.class, () -> HashDeClaves.calcular(""));
    }
}
