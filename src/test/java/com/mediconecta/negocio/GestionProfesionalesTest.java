package com.mediconecta.negocio;

import com.mediconecta.modelo.Disponibilidad;
import com.mediconecta.modelo.Profesional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Padron de profesionales, agenda semanal y calculo de horarios libres. */
class GestionProfesionalesTest {

    private Escenario e;

    @BeforeEach
    void preparar() {
        e = new Escenario();
    }

    private static Disponibilidad franja(DayOfWeek dia, int desde, int hasta, int minutos) {
        return new Disponibilidad(dia, LocalTime.of(desde, 0), LocalTime.of(hasta, 0), minutos);
    }

    private Profesional nuevo(String nombre, String especialidad, String matricula) {
        return new Profesional(nombre, especialidad, matricula);
    }

    @Test
    void consultas() {
        assertEquals(2, e.gestionProfesionales.listarProfesionales().size());
        assertEquals(2, e.gestionProfesionales.listarPorEspecialidad(null).size());
        assertEquals(2, e.gestionProfesionales.listarPorEspecialidad(" ").size());
        assertEquals(1, e.gestionProfesionales.listarPorEspecialidad(" cardiologia ").size());
        assertEquals(List.of("Cardiologia", "Traumatologia"), e.gestionProfesionales.listarEspecialidades());
        assertEquals("Dr. Martin Ruiz", e.gestionProfesionales.obtener(e.martin.getId()).getNombre());
        assertThrows(NoEncontradoException.class, () -> e.gestionProfesionales.obtener(999L));
    }

    @Test
    void miPerfilDevuelveLaFichaDelMedico() {
        e.como("medico", Roles.MEDICO);
        assertEquals(e.laura.getId(), e.gestionProfesionales.miPerfilProfesional().getId());
        e.como("otro-medico", Roles.MEDICO);
        assertThrows(NoEncontradoException.class, () -> e.gestionProfesionales.miPerfilProfesional());
    }

    @Test
    void registraUnProfesionalNormalizandoLosDatos() {
        Profesional pedido = nuevo(" Dra. Paula Rios ", " Pediatria ", " MP-1 ");
        pedido.setDisponibilidades(null);
        Profesional creado = e.gestionProfesionales.registrarProfesional(pedido);
        assertNotNull(creado.getId());
        assertEquals("Dra. Paula Rios", creado.getNombre());
        assertEquals("Pediatria", creado.getEspecialidad());
        assertEquals("MP-1", creado.getMatricula());
        assertTrue(creado.isActivo());
        assertTrue(creado.getDisponibilidades().isEmpty());
    }

    @Test
    void elAltaValidaLosDatos() {
        assertThrows(ReglaNegocioException.class, () -> e.gestionProfesionales.registrarProfesional(null));
        assertThrows(ReglaNegocioException.class,
                () -> e.gestionProfesionales.registrarProfesional(nuevo(" ", "Pediatria", "MP-1")));
        assertThrows(ReglaNegocioException.class,
                () -> e.gestionProfesionales.registrarProfesional(nuevo("Paula", null, "MP-1")));
        assertThrows(ReglaNegocioException.class,
                () -> e.gestionProfesionales.registrarProfesional(nuevo("Paula", "Pediatria", "")));
        assertThrows(ReglaNegocioException.class,
                () -> e.gestionProfesionales.registrarProfesional(nuevo("Paula", "Pediatria", "MP-10234")));

        Profesional conFranjaMala = nuevo("Paula", "Pediatria", "MP-2");
        conFranjaMala.getDisponibilidades().add(franja(DayOfWeek.MONDAY, 12, 9, 30));
        assertThrows(ReglaNegocioException.class, () -> e.gestionProfesionales.registrarProfesional(conFranjaMala));
    }

    @Test
    void validaCadaFranja() {
        Long id = e.martin.getId();
        assertThrows(ReglaNegocioException.class, () -> e.gestionProfesionales.definirDisponibilidad(id, null));
        assertThrows(ReglaNegocioException.class, () -> e.gestionProfesionales.definirDisponibilidad(id, List.of()));
        assertThrows(ReglaNegocioException.class, () -> e.gestionProfesionales.definirDisponibilidad(id,
                List.of(new Disponibilidad(null, LocalTime.of(9, 0), LocalTime.of(10, 0), 30))));
        assertThrows(ReglaNegocioException.class, () -> e.gestionProfesionales.definirDisponibilidad(id,
                List.of(new Disponibilidad(DayOfWeek.MONDAY, null, LocalTime.of(10, 0), 30))));
        assertThrows(ReglaNegocioException.class, () -> e.gestionProfesionales.definirDisponibilidad(id,
                List.of(new Disponibilidad(DayOfWeek.MONDAY, LocalTime.of(9, 0), null, 30))));
        assertThrows(ReglaNegocioException.class, () -> e.gestionProfesionales.definirDisponibilidad(id,
                List.of(franja(DayOfWeek.MONDAY, 10, 10, 30))));
        assertThrows(ReglaNegocioException.class, () -> e.gestionProfesionales.definirDisponibilidad(id,
                List.of(franja(DayOfWeek.MONDAY, 9, 10, 0))));
    }

    @Test
    void reemplazaLaAgenda() {
        List<Disponibilidad> franjas = new ArrayList<>(List.of(franja(DayOfWeek.TUESDAY, 8, 10, 20)));
        Profesional actualizado = e.gestionProfesionales.definirDisponibilidad(e.martin.getId(), franjas);
        assertEquals(1, actualizado.getDisponibilidades().size());
        assertEquals(DayOfWeek.TUESDAY, actualizado.getDisponibilidades().get(0).getDiaSemana());
    }

    @Test
    void unMedicoSoloModificaSuPropiaAgenda() {
        e.como("medico", Roles.MEDICO);
        List<Disponibilidad> franjas = new ArrayList<>(List.of(franja(DayOfWeek.FRIDAY, 8, 9, 30)));
        assertThrows(AccesoDenegadoException.class,
                () -> e.gestionProfesionales.definirDisponibilidad(e.martin.getId(), franjas));
        assertEquals(1, e.gestionProfesionales.definirDisponibilidad(e.laura.getId(), franjas)
                .getDisponibilidades().size());
    }

    @Test
    void losHorariosLibresSalenDeLaAgendaMenosLosOcupados() {
        LocalDate lunes = LocalDate.now().plusDays(1).with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        List<LocalDateTime> libres = e.gestionProfesionales.horariosLibres(e.laura.getId(), lunes);
        assertEquals(8, libres.size());
        assertEquals(lunes.atTime(9, 0), libres.get(0));
        assertEquals(lunes.atTime(12, 30), libres.get(7));

        SolicitudTurno solicitud = e.solicitudParaAna(lunes.atTime(9, 30).format(com.mediconecta.modelo.Turno.FORMATO_FECHA_HORA));
        e.fachada.reservar(solicitud);
        List<LocalDateTime> despues = e.gestionProfesionales.horariosLibres(e.laura.getId(), lunes);
        assertEquals(7, despues.size());
        assertFalse(despues.contains(lunes.atTime(9, 30)));

        LocalDate domingo = lunes.minusDays(1);
        assertTrue(e.gestionProfesionales.horariosLibres(e.laura.getId(), domingo).isEmpty());
        assertTrue(e.gestionProfesionales.horariosLibres(e.laura.getId(), LocalDate.now().minusDays(7)).isEmpty());
        assertThrows(ReglaNegocioException.class, () -> e.gestionProfesionales.horariosLibres(e.laura.getId(), null));
    }
}
