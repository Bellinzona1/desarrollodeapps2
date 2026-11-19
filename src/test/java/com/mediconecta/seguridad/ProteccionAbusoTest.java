package com.mediconecta.seguridad;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Ventana deslizante y limites contra fuerza bruta. */
class ProteccionAbusoTest {

    static final class Reloj extends Clock {
        Instant ahora = Instant.parse("2026-11-20T10:00:00Z");

        @Override
        public ZoneOffset getZone() {
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

    @Test
    void laVentanaBloqueaAlLlegarAlMaximoYLiberaCuandoVenceElMasViejo() {
        Reloj reloj = new Reloj();
        LimitadorDeIntentos limitador = new LimitadorDeIntentos(3, Duration.ofMinutes(1), reloj);
        assertEquals(0, limitador.segundosDeBloqueo("k"));

        limitador.registrar("k");
        reloj.ahora = reloj.ahora.plusSeconds(20);
        limitador.registrar("k");
        limitador.registrar("k");
        assertEquals(40, limitador.segundosDeBloqueo("k"));

        reloj.ahora = reloj.ahora.plusSeconds(41);
        assertEquals(0, limitador.segundosDeBloqueo("k"));
        assertEquals(0, limitador.segundosDeBloqueo("otra"));
    }

    @Test
    void consumirCuentaSoloLosPedidosPermitidos() {
        Reloj reloj = new Reloj();
        LimitadorDeIntentos limitador = new LimitadorDeIntentos(2, Duration.ofSeconds(10), reloj);
        assertEquals(0, limitador.consumir("u"));
        assertEquals(0, limitador.consumir("u"));
        assertTrue(limitador.consumir("u") > 0);
        limitador.olvidar("u");
        assertEquals(0, limitador.consumir("u"));
    }

    @Test
    void conMuchasClavesPurgaLasVencidasSinPerderLasVigentes() {
        Reloj reloj = new Reloj();
        LimitadorDeIntentos limitador = new LimitadorDeIntentos(1, Duration.ofSeconds(10), reloj);
        for (int i = 0; i < 10_001; i++) {
            limitador.registrar("vieja-" + i);
        }
        reloj.ahora = reloj.ahora.plusSeconds(11);
        limitador.registrar("nueva");
        limitador.registrar("nueva-2");
        assertTrue(limitador.segundosDeBloqueo("nueva") > 0);
        assertEquals(0, limitador.segundosDeBloqueo("vieja-1"));
    }

    @Test
    void validaLaConfiguracion() {
        assertThrows(IllegalArgumentException.class, () -> new LimitadorDeIntentos(0, Duration.ofSeconds(1), Clock.systemUTC()));
        assertThrows(IllegalArgumentException.class, () -> new LimitadorDeIntentos(1, Duration.ZERO, Clock.systemUTC()));
        assertThrows(IllegalArgumentException.class, () -> new LimitadorDeIntentos(1, Duration.ofSeconds(-1), Clock.systemUTC()));
    }

    @Test
    void elLoginSeBloqueaPorCuentaSinDistinguirMayusculas() {
        ProteccionAbuso proteccion = new ProteccionAbuso(new Reloj());
        for (int i = 0; i < ProteccionAbuso.FALLOS_POR_CUENTA; i++) {
            proteccion.loginFallido(i % 2 == 0 ? "Ana" : " ana ", null);
        }
        assertTrue(proteccion.bloqueoDeLogin("ANA", "1.1.1.1") > 0);
        assertEquals(0, proteccion.bloqueoDeLogin("otra", null));
        proteccion.loginCorrecto("ana");
        assertEquals(0, proteccion.bloqueoDeLogin("ana", null));
        assertEquals(0, proteccion.bloqueoDeLogin(null, null));
    }

    @Test
    void laOrientacionTieneSuPropioLimitePorMinuto() {
        ProteccionAbuso proteccion = new ProteccionAbuso();
        for (int i = 0; i < ProteccionAbuso.ORIENTACIONES_POR_MINUTO; i++) {
            assertEquals(0, proteccion.consumirOrientacion("paciente"));
        }
        assertTrue(proteccion.consumirOrientacion("paciente") > 0);
        assertEquals(0, proteccion.consumirOrientacion("recepcion"));
    }
}
