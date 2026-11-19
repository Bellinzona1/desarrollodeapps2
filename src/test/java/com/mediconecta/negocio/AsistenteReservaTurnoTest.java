package com.mediconecta.negocio;

import com.mediconecta.modelo.Turno;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** La conversacion del componente stateful: el estado vive en la instancia entre llamadas. */
class AsistenteReservaTurnoTest {

    private Escenario e;

    private AsistenteReservaTurno asistente;

    private String horario;

    @BeforeEach
    void preparar() {
        e = new Escenario();
        asistente = new AsistenteReservaTurno();
        Inyeccion.campo(asistente, "fachadaReservas", e.fachada);
        Inyeccion.campo(asistente, "gestionPacientes", e.gestionPacientes);
        Inyeccion.campo(asistente, "gestionProfesionales", e.gestionProfesionales);
        Inyeccion.campo(asistente, "fabricaTurnos", e.fabrica);
        asistente.alCrear();
        horario = Escenario.proximoHabil(11, 0);
    }

    @Test
    void recorreLosCuatroPasosYConfirma() {
        assertEquals(AsistenteReservaTurno.SIN_INICIAR, asistente.estado().getPaso());
        assertNotNull(asistente.estado().getIdConversacion());

        assertEquals(AsistenteReservaTurno.ELEGIR_ESPECIALIDAD, asistente.iniciar("30111222").getPaso());
        assertEquals(AsistenteReservaTurno.ELEGIR_PROFESIONAL, asistente.elegirEspecialidad(" Cardiologia ").getPaso());
        assertEquals(AsistenteReservaTurno.ELEGIR_HORARIO, asistente.elegirProfesional(e.laura.getId()).getPaso());
        assertFalse(asistente.horariosDisponibles(LocalDateTime.parse(horario).toLocalDate()).isEmpty());
        assertEquals(AsistenteReservaTurno.CONFIRMAR, asistente.elegirHorario(horario).getPaso());

        EstadoReserva final_ = asistente.confirmar();
        assertTrue(final_.isFinalizada());
        assertNotNull(final_.getTurnoId());
        assertEquals("Ana Gomez", final_.getNombrePaciente());
        assertEquals("Dra. Laura Fernandez", final_.getNombreProfesional());
        assertEquals(1, e.notificador.enviados.size());
        asistente.finalizar();
        asistente.alDestruir();
    }

    @Test
    void cadaPasoExigeElAnterior() {
        assertThrows(ReglaNegocioException.class, () -> asistente.elegirEspecialidad("Cardiologia"));
        assertThrows(ReglaNegocioException.class, () -> asistente.elegirProfesional(1L));
        assertThrows(ReglaNegocioException.class, () -> asistente.elegirHorario(horario));
        assertThrows(ReglaNegocioException.class, () -> asistente.confirmar());
        assertThrows(ReglaNegocioException.class, () -> asistente.horariosDisponibles(null));
        assertThrows(ReglaNegocioException.class, () -> asistente.volver());
    }

    @Test
    void validaLoElegidoEnCadaPaso() {
        asistente.iniciar("30111222");
        assertThrows(ReglaNegocioException.class, () -> asistente.elegirEspecialidad(" "));
        assertThrows(ReglaNegocioException.class, () -> asistente.elegirEspecialidad(null));
        assertThrows(ReglaNegocioException.class, () -> asistente.elegirEspecialidad("Dermatologia"));
        asistente.elegirEspecialidad("Cardiologia");
        assertThrows(ReglaNegocioException.class, () -> asistente.elegirProfesional(null));
        assertThrows(ReglaNegocioException.class, () -> asistente.elegirProfesional(e.martin.getId()));
        asistente.elegirProfesional(e.laura.getId());
        assertThrows(ReglaNegocioException.class, () -> asistente.elegirHorario(" "));
        assertThrows(ReglaNegocioException.class, () -> asistente.elegirHorario(null));
        String fueraDeAgenda = LocalDateTime.parse(horario).withHour(20).format(Turno.FORMATO_FECHA_HORA);
        assertThrows(ReglaNegocioException.class, () -> asistente.elegirHorario(fueraDeAgenda));
    }

    @Test
    void volverDeshaceLaUltimaEleccion() {
        asistente.iniciar("30111222");
        asistente.elegirEspecialidad("Cardiologia");
        asistente.elegirProfesional(e.laura.getId());
        asistente.elegirHorario(horario);

        EstadoReserva estado = asistente.volver();
        assertEquals(AsistenteReservaTurno.ELEGIR_HORARIO, estado.getPaso());
        assertNull(estado.getFechaHora());
        assertEquals(e.laura.getId(), estado.getProfesionalId());

        estado = asistente.volver();
        assertEquals(AsistenteReservaTurno.ELEGIR_PROFESIONAL, estado.getPaso());
        assertNull(estado.getProfesionalId());
        assertEquals("Cardiologia", estado.getEspecialidad());

        estado = asistente.volver();
        assertEquals(AsistenteReservaTurno.ELEGIR_ESPECIALIDAD, estado.getPaso());
        assertNull(estado.getEspecialidad());
        assertThrows(ReglaNegocioException.class, () -> asistente.volver());
    }

    @Test
    void despuesDeConfirmarNoSePuedeVolver() {
        asistente.iniciar("30111222");
        asistente.elegirEspecialidad("Cardiologia");
        asistente.elegirProfesional(e.laura.getId());
        asistente.elegirHorario(horario);
        asistente.confirmar();
        assertThrows(ReglaNegocioException.class, () -> asistente.volver());
    }

    @Test
    void unPacienteSoloIniciaReservasParaSiMismo() {
        e.comoPaciente();
        assertThrows(AccesoDenegadoException.class, () -> asistente.iniciar("28999888"));
        assertEquals(AsistenteReservaTurno.ELEGIR_ESPECIALIDAD, asistente.iniciar("30111222").getPaso());
    }
}
