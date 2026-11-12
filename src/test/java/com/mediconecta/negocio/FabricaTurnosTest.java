package com.mediconecta.negocio;

import com.mediconecta.modelo.EstadoTurno;
import com.mediconecta.modelo.Paciente;
import com.mediconecta.modelo.Profesional;
import com.mediconecta.modelo.Turno;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Patron Factory: invariantes con las que nace todo turno. */
class FabricaTurnosTest {

    private final FabricaTurnos fabrica = new FabricaTurnos();

    private final Paciente paciente = new Paciente("Ana Gomez", "30111222");

    private final Profesional profesional = new Profesional("Dra. Laura Fernandez", "Cardiologia", "MP-1");

    @Test
    void todoTurnoNaceProgramadoConLaFechaTruncadaAlMinuto() {
        Turno turno = fabrica.crear(paciente, profesional, null,
                LocalDateTime.of(2026, 12, 1, 9, 30, 59), "  ");

        assertEquals(EstadoTurno.PROGRAMADO, turno.getEstado());
        assertEquals(LocalDateTime.of(2026, 12, 1, 9, 30), turno.getFechaHora());
        assertEquals("Cardiologia", turno.getEspecialidad());
        assertNull(turno.getMotivo());
        assertFalse(turno.isArchivado());
    }

    @Test
    void laEspecialidadTieneQueSerLaDelProfesional() {
        assertThrows(ReglaNegocioException.class, () -> fabrica.crear(paciente, profesional,
                "Pediatria", LocalDateTime.of(2026, 12, 1, 9, 30), null));
    }

    @Test
    void sinPacienteOSinProfesionalNoHayTurno() {
        LocalDateTime momento = LocalDateTime.of(2026, 12, 1, 9, 30);
        assertThrows(ReglaNegocioException.class, () -> fabrica.crear(null, profesional, null, momento, null));
        assertThrows(ReglaNegocioException.class, () -> fabrica.crear(paciente, null, null, momento, null));
    }

    @Test
    void unaFechaMalEscritaEsUnaReglaDeNegocio() {
        assertThrows(ReglaNegocioException.class, () -> fabrica.interpretarFecha("mañana a las 9"));
        assertThrows(ReglaNegocioException.class, () -> fabrica.interpretarFecha(null));
    }
}
