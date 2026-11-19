package com.mediconecta.integraciones;

import com.mediconecta.negocio.IntegracionExternaException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Timeout, circuit breaker y fallback de las integraciones externas. */
class ProteccionIntegracionTest {

    private ExecutorService ejecutor;

    private RelojManual reloj;

    private ProteccionIntegracion proteccion;

    private final AtomicInteger llamadas = new AtomicInteger();

    private final AtomicReference<IntegracionExternaException> ultimaFalla = new AtomicReference<>();

    /** Reloj que los tests adelantan a mano. */
    static final class RelojManual extends Clock {
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

    @BeforeEach
    void preparar() {
        ejecutor = Executors.newCachedThreadPool();
        reloj = new RelojManual();
        proteccion = new ProteccionIntegracion(reloj, ejecutor, Duration.ofMillis(300));
    }

    @AfterEach
    void cerrar() {
        ejecutor.shutdownNow();
    }

    private String exitosa() {
        llamadas.incrementAndGet();
        return "ok";
    }

    private String fallida() {
        llamadas.incrementAndGet();
        throw new IntegracionExternaException("caido");
    }

    private String ejecutar(java.util.concurrent.Callable<String> llamada) {
        return proteccion.ejecutar("sistema", llamada, falla -> {
            ultimaFalla.set(falla);
            return "fallback";
        });
    }

    @Test
    void siLaLlamadaSaleBienDevuelveSuResultado() {
        assertEquals("ok", ejecutar(this::exitosa));
        assertEquals(ProteccionIntegracion.Estado.CERRADO, proteccion.estado("sistema"));
        assertEquals(ProteccionIntegracion.Estado.CERRADO, proteccion.estado("otro"));
    }

    @Test
    void siFallaDevuelveElFallbackConLaCausa() {
        assertEquals("fallback", ejecutar(this::fallida));
        assertEquals("caido", ultimaFalla.get().getMessage());
    }

    @Test
    void unaLlamadaLentaSeCortaPorTimeout() {
        long inicio = System.nanoTime();
        assertEquals("fallback", ejecutar(() -> {
            Thread.sleep(5_000);
            return "tarde";
        }));
        long milisegundos = Duration.ofNanos(System.nanoTime() - inicio).toMillis();
        assertTrue(milisegundos < 2_000, "no tiene que esperar a la llamada lenta: " + milisegundos + " ms");
        assertTrue(ultimaFalla.get().getMessage().contains("no respondio"));
    }

    @Test
    void cualquierOtraExcepcionSeTraduceAFallaDeIntegracion() {
        assertEquals("fallback", ejecutar(() -> {
            throw new IllegalStateException("bug del cliente");
        }));
        assertTrue(ultimaFalla.get().getCause() instanceof IllegalStateException);
    }

    @Test
    void trasTresFallasElCircuitoSeAbreYNoSeLlamaAlSistema() {
        for (int i = 0; i < ProteccionIntegracion.FALLOS_PARA_ABRIR; i++) {
            ejecutar(this::fallida);
        }
        assertEquals(ProteccionIntegracion.Estado.ABIERTO, proteccion.estado("sistema"));
        int antes = llamadas.get();

        assertEquals("fallback", ejecutar(this::exitosa));
        assertEquals(antes, llamadas.get(), "con el circuito abierto no se llama al sistema externo");
        assertTrue(ultimaFalla.get().getMessage().contains("fuera de servicio"));
    }

    @Test
    void pasadoElTiempoDejaPasarUnaPruebaYSiSaleBienSeCierra() {
        for (int i = 0; i < ProteccionIntegracion.FALLOS_PARA_ABRIR; i++) {
            ejecutar(this::fallida);
        }
        reloj.ahora = reloj.ahora.plus(ProteccionIntegracion.TIEMPO_ABIERTO).plusSeconds(1);
        assertEquals(ProteccionIntegracion.Estado.SEMIABIERTO, proteccion.estado("sistema"));

        assertEquals("ok", ejecutar(this::exitosa));
        assertEquals(ProteccionIntegracion.Estado.CERRADO, proteccion.estado("sistema"));
    }

    @Test
    void siLaPruebaFallaElCircuitoVuelveAAbrirse() {
        for (int i = 0; i < ProteccionIntegracion.FALLOS_PARA_ABRIR; i++) {
            ejecutar(this::fallida);
        }
        reloj.ahora = reloj.ahora.plus(ProteccionIntegracion.TIEMPO_ABIERTO).plusSeconds(1);
        assertEquals("fallback", ejecutar(this::fallida));
        assertEquals(ProteccionIntegracion.Estado.ABIERTO, proteccion.estado("sistema"));
    }

    @Test
    void unExitoReiniciaElContadorDeFallas() {
        ejecutar(this::fallida);
        ejecutar(this::fallida);
        ejecutar(this::exitosa);
        ejecutar(this::fallida);
        ejecutar(this::fallida);
        assertEquals(ProteccionIntegracion.Estado.CERRADO, proteccion.estado("sistema"));
    }

    @Test
    void sinEjecutorPropioUsaUnoDeRespaldo() {
        ProteccionIntegracion porDefecto = new ProteccionIntegracion();
        assertEquals("ok", porDefecto.ejecutar("x", () -> "ok", falla -> "fallback"));
    }
}
