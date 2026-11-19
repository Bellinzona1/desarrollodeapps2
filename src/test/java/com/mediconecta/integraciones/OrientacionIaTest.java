package com.mediconecta.integraciones;

import com.mediconecta.negocio.DemasiadosIntentosException;
import com.mediconecta.negocio.Escenario;
import com.mediconecta.negocio.Inyeccion;
import com.mediconecta.negocio.IntegracionExternaException;
import com.mediconecta.negocio.ReglaNegocioException;
import com.mediconecta.seguridad.ProteccionAbuso;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Flujo completo de la orientacion con IA en {@link GestorOrientacion}, con un
 * proveedor de prueba: nunca se llama a un servicio externo real.
 */
class OrientacionIaTest {

    private Escenario e;

    private GestorOrientacion gestor;

    private ExecutorService ejecutor;

    /** Lo que "responde la IA" en cada test; si es null, la IA falla. */
    private SugerenciaEspecialidad respuestaDeLaIa;

    private final AtomicInteger llamadasALaIa = new AtomicInteger();

    private final List<MensajeChat> conversacion =
            List.of(new MensajeChat(MensajeChat.USUARIO, "Me torci el tobillo jugando al futbol"));

    @BeforeEach
    void preparar() {
        e = new Escenario();
        ejecutor = Executors.newCachedThreadPool();
        gestor = new GestorOrientacion();
        Inyeccion.campo(gestor, "gestionProfesionales", e.gestionProfesionales);
        Inyeccion.campo(gestor, "orientador", (OrientadorEspecialidad) (mensajes, especialidades) -> {
            llamadasALaIa.incrementAndGet();
            if (respuestaDeLaIa == null) {
                throw new IntegracionExternaException("El asistente de IA no respondio.");
            }
            return respuestaDeLaIa;
        });
        Inyeccion.campo(gestor, "proteccion", new ProteccionIntegracion(Clock.systemUTC(), ejecutor, Duration.ofSeconds(2)));
        Inyeccion.campo(gestor, "proteccionAbuso", new ProteccionAbuso());
        Inyeccion.campo(gestor, "contexto", e.contexto);
        e.comoPaciente();
    }

    @AfterEach
    void cerrar() {
        ejecutor.shutdownNow();
    }

    private static SugerenciaEspecialidad deLaIa(String mensaje, String especialidad) {
        SugerenciaEspecialidad sugerencia = new SugerenciaEspecialidad(mensaje, especialidad, false);
        sugerencia.setOrigen("IA");
        return sugerencia;
    }

    @Test
    void devuelveLaSugerenciaDeLaIaConElNombreDeLaLista() {
        respuestaDeLaIa = deLaIa("Te conviene Traumatologia.", "traumatología");
        SugerenciaEspecialidad sugerencia = gestor.sugerirEspecialidad(conversacion);
        assertEquals("Traumatologia", sugerencia.getEspecialidad());
        assertEquals("IA", sugerencia.getOrigen());
    }

    @Test
    void descartaUnaEspecialidadInventadaPorElModelo() {
        respuestaDeLaIa = deLaIa("Te conviene Dermatologia.", "Dermatologia");
        assertNull(gestor.sugerirEspecialidad(conversacion).getEspecialidad());
    }

    @Test
    void siLaIaFallaRespondeElFallbackPorPalabrasClave() {
        respuestaDeLaIa = null;
        SugerenciaEspecialidad sugerencia = gestor.sugerirEspecialidad(conversacion);
        assertEquals("Traumatologia", sugerencia.getEspecialidad());
        assertEquals(OrientadorPorPalabrasClave.ORIGEN, sugerencia.getOrigen());
        assertTrue(sugerencia.getMensaje().contains("no esta disponible"));
    }

    @Test
    void siLaIaTardaMasDelTimeoutRespondeElFallback() {
        Inyeccion.campo(gestor, "orientador", (OrientadorEspecialidad) (mensajes, especialidades) -> {
            try {
                Thread.sleep(10_000);
            } catch (InterruptedException interrumpido) {
                Thread.currentThread().interrupt();
            }
            return deLaIa("tarde", "Cardiologia");
        });
        Inyeccion.campo(gestor, "proteccion", new ProteccionIntegracion(Clock.systemUTC(), ejecutor, Duration.ofMillis(200)));
        assertEquals(OrientadorPorPalabrasClave.ORIGEN, gestor.sugerirEspecialidad(conversacion).getOrigen());
    }

    @Test
    void validaLaConversacionAntesDeLlamarALaIa() {
        assertThrows(ReglaNegocioException.class, () -> gestor.sugerirEspecialidad(List.of()));
        assertEquals(0, llamadasALaIa.get());
    }

    @Test
    void sinEspecialidadesActivasNoHayOrientacion() {
        e.laura.setActivo(false);
        e.martin.setActivo(false);
        respuestaDeLaIa = deLaIa("x", null);
        assertThrows(ReglaNegocioException.class, () -> gestor.sugerirEspecialidad(conversacion));
    }

    @Test
    void masDeDiezConsultasPorMinutoDevuelven429() {
        respuestaDeLaIa = deLaIa("ok", null);
        for (int i = 0; i < 10; i++) {
            gestor.sugerirEspecialidad(conversacion);
        }
        DemasiadosIntentosException limite = assertThrows(DemasiadosIntentosException.class,
                () -> gestor.sugerirEspecialidad(conversacion));
        assertTrue(limite.getSegundosDeEspera() <= 60);
        assertEquals(10, llamadasALaIa.get());

        e.como("recepcion", "RECEPCION");
        assertFalse(gestor.sugerirEspecialidad(conversacion).getMensaje().isEmpty(), "el limite es por usuario");
    }
}
