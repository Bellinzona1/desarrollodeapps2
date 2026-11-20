package com.mediconecta.integraciones;

import com.mediconecta.integraciones.DisyuntorDeIntegracion.Estado;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Patron Circuit Breaker: transiciones CERRADO -> ABIERTO -> SEMIABIERTO -> CERRADO. */
class DisyuntorDeIntegracionTest {

    private static final String SISTEMA = DisyuntorDeIntegracion.REST_PARTNER;

    /** Reloj que avanza solo cuando el test lo pide. */
    static final class RelojManual extends Clock {
        private Instant ahora = Instant.parse("2026-11-20T12:00:00Z");

        void avanzar(Duration tiempo) {
            ahora = ahora.plus(tiempo);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zona) {
            return this;
        }

        @Override
        public Instant instant() {
            return ahora;
        }
    }

    private final RelojManual reloj = new RelojManual();
    private DisyuntorDeIntegracion disyuntor;

    static DisyuntorDeIntegracion conReloj(Clock reloj) {
        DisyuntorDeIntegracion disyuntor = new DisyuntorDeIntegracion();
        try {
            Field campo = DisyuntorDeIntegracion.class.getDeclaredField("reloj");
            campo.setAccessible(true);
            campo.set(disyuntor, reloj);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        return disyuntor;
    }

    @BeforeEach
    void preparar() {
        disyuntor = conReloj(reloj);
    }

    private void fallar(int veces) {
        for (int i = 0; i < veces; i++) {
            assertTrue(disyuntor.permiteLlamar(SISTEMA));
            disyuntor.registrarFalla(SISTEMA);
        }
    }

    @Test
    void empiezaCerradoYDejaPasarLasLlamadas() {
        assertEquals(Estado.CERRADO, disyuntor.estado(SISTEMA));
        assertTrue(disyuntor.permiteLlamar(SISTEMA));
    }

    @Test
    void menosFallasQueElUmbralNoLoAbren() {
        fallar(DisyuntorDeIntegracion.FALLAS_PARA_ABRIR - 1);
        assertEquals(Estado.CERRADO, disyuntor.estado(SISTEMA));
        assertTrue(disyuntor.permiteLlamar(SISTEMA));
    }

    @Test
    void unExitoReiniciaLaCuentaDeFallasSeguidas() {
        fallar(DisyuntorDeIntegracion.FALLAS_PARA_ABRIR - 1);
        disyuntor.registrarExito(SISTEMA);
        fallar(DisyuntorDeIntegracion.FALLAS_PARA_ABRIR - 1);
        assertEquals(Estado.CERRADO, disyuntor.estado(SISTEMA));
    }

    @Test
    void conTresFallasSeguidasSeAbreYNoDejaLlamar() {
        fallar(DisyuntorDeIntegracion.FALLAS_PARA_ABRIR);
        assertEquals(Estado.ABIERTO, disyuntor.estado(SISTEMA));
        assertFalse(disyuntor.permiteLlamar(SISTEMA));
        reloj.avanzar(DisyuntorDeIntegracion.ESPERA_ABIERTO.minusSeconds(1));
        assertFalse(disyuntor.permiteLlamar(SISTEMA));
    }

    @Test
    void pasadaLaEsperaDejaPasarUnSoloPedidoDePrueba() {
        fallar(DisyuntorDeIntegracion.FALLAS_PARA_ABRIR);
        reloj.avanzar(DisyuntorDeIntegracion.ESPERA_ABIERTO);
        assertEquals(Estado.SEMIABIERTO, disyuntor.estado(SISTEMA));
        assertTrue(disyuntor.permiteLlamar(SISTEMA));
        assertFalse(disyuntor.permiteLlamar(SISTEMA), "mientras la prueba esta en curso no pasa otro pedido");
    }

    @Test
    void siLaPruebaSaleBienSeCierra() {
        fallar(DisyuntorDeIntegracion.FALLAS_PARA_ABRIR);
        reloj.avanzar(DisyuntorDeIntegracion.ESPERA_ABIERTO);
        assertTrue(disyuntor.permiteLlamar(SISTEMA));
        disyuntor.registrarExito(SISTEMA);
        assertEquals(Estado.CERRADO, disyuntor.estado(SISTEMA));
        assertTrue(disyuntor.permiteLlamar(SISTEMA));
    }

    @Test
    void siLaPruebaFallaSeVuelveAAbrir() {
        fallar(DisyuntorDeIntegracion.FALLAS_PARA_ABRIR);
        reloj.avanzar(DisyuntorDeIntegracion.ESPERA_ABIERTO);
        assertTrue(disyuntor.permiteLlamar(SISTEMA));
        disyuntor.registrarFalla(SISTEMA);
        assertEquals(Estado.ABIERTO, disyuntor.estado(SISTEMA));
        assertFalse(disyuntor.permiteLlamar(SISTEMA));
    }

    @Test
    void siLaPruebaNuncaInformaResultadoDespuesDeLaEsperaPasaOtra() {
        fallar(DisyuntorDeIntegracion.FALLAS_PARA_ABRIR);
        reloj.avanzar(DisyuntorDeIntegracion.ESPERA_ABIERTO);
        assertTrue(disyuntor.permiteLlamar(SISTEMA));
        reloj.avanzar(DisyuntorDeIntegracion.ESPERA_ABIERTO);
        assertTrue(disyuntor.permiteLlamar(SISTEMA));
    }

    @Test
    void cadaSistemaTieneSuPropioCircuito() {
        fallar(DisyuntorDeIntegracion.FALLAS_PARA_ABRIR);
        assertTrue(disyuntor.permiteLlamar(DisyuntorDeIntegracion.SOAP_LEGADO));
        assertTrue(disyuntor.permiteLlamar(DisyuntorDeIntegracion.IA));
        Map<String, Estado> estados = disyuntor.estados();
        assertEquals(Estado.ABIERTO, estados.get(SISTEMA));
        assertEquals(Estado.CERRADO, estados.get(DisyuntorDeIntegracion.SOAP_LEGADO));
    }
}
