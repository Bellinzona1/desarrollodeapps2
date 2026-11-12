package com.mediconecta.modelo.estado;

import com.mediconecta.modelo.EstadoTurno;
import com.mediconecta.modelo.Turno;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Patron State: transiciones validas e invalidas del turno. */
class EstadoDelTurnoTest {

    private static Turno turnoEn(EstadoTurno estado) {
        Turno turno = new Turno();
        turno.setId(7L);
        turno.setEstado(estado);
        return turno;
    }

    @Nested
    @DisplayName("Desde PROGRAMADO")
    class DesdeProgramado {

        @Test
        void cancelarLoPasaACancelado() {
            Turno turno = turnoEn(EstadoTurno.PROGRAMADO);
            turno.cancelar();
            assertEquals(EstadoTurno.CANCELADO, turno.getEstado());
        }

        @Test
        void atenderLoPasaAAtendido() {
            Turno turno = turnoEn(EstadoTurno.PROGRAMADO);
            turno.atender();
            assertEquals(EstadoTurno.ATENDIDO, turno.getEstado());
        }

        @Test
        void archivarEsInvalido() {
            Turno turno = turnoEn(EstadoTurno.PROGRAMADO);
            TransicionInvalidaException e = assertThrows(TransicionInvalidaException.class, turno::archivar);
            assertEquals(EstadoTurno.PROGRAMADO, e.getEstadoActual());
            assertEquals("archivar", e.getOperacion());
            assertFalse(turno.isArchivado());
        }

        @Test
        void ocupaElHorario() {
            assertTrue(turnoEn(EstadoTurno.PROGRAMADO).ocupaHorario());
        }
    }

    @Nested
    @DisplayName("Desde CANCELADO")
    class DesdeCancelado {

        @Test
        void cancelarDosVecesEsInvalido() {
            Turno turno = turnoEn(EstadoTurno.CANCELADO);
            TransicionInvalidaException e = assertThrows(TransicionInvalidaException.class, turno::cancelar);
            assertTrue(e.getMessage().contains("ya estaba cancelado"));
            assertEquals(EstadoTurno.CANCELADO, turno.getEstado());
        }

        @Test
        void atenderEsInvalido() {
            Turno turno = turnoEn(EstadoTurno.CANCELADO);
            assertThrows(TransicionInvalidaException.class, turno::atender);
            assertEquals(EstadoTurno.CANCELADO, turno.getEstado());
        }

        @Test
        void archivarLoSacaDeLaAgendaYSigueCancelado() {
            Turno turno = turnoEn(EstadoTurno.CANCELADO);
            turno.archivar();
            assertTrue(turno.isArchivado());
            assertEquals(EstadoTurno.CANCELADO, turno.getEstado());
        }

        @Test
        void archivarDosVecesEsInvalido() {
            Turno turno = turnoEn(EstadoTurno.CANCELADO);
            turno.archivar();
            TransicionInvalidaException e = assertThrows(TransicionInvalidaException.class, turno::archivar);
            assertTrue(e.getMessage().contains("ya estaba archivado"));
        }

        @Test
        void liberaElHorario() {
            assertFalse(turnoEn(EstadoTurno.CANCELADO).ocupaHorario());
        }
    }

    @Nested
    @DisplayName("Desde ATENDIDO")
    class DesdeAtendido {

        @Test
        void noAdmiteNingunaTransicion() {
            Turno turno = turnoEn(EstadoTurno.ATENDIDO);
            assertThrows(TransicionInvalidaException.class, turno::cancelar);
            assertThrows(TransicionInvalidaException.class, turno::atender);
            assertThrows(TransicionInvalidaException.class, turno::archivar);
            assertEquals(EstadoTurno.ATENDIDO, turno.getEstado());
            assertFalse(turno.isArchivado());
        }
    }

    @Test
    void cadaValorDelEnumTieneSuObjetoEstado() {
        for (EstadoTurno estado : EstadoTurno.values()) {
            assertEquals(estado, estado.comportamiento().valor());
        }
    }
}
