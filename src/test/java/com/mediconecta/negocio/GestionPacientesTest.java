package com.mediconecta.negocio;

import com.mediconecta.modelo.Paciente;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Padron de pacientes y el ownership del rol PACIENTE. */
class GestionPacientesTest {

    private Escenario escenario;

    private GestionPacientes gestion;

    @BeforeEach
    void preparar() {
        escenario = new Escenario();
        gestion = escenario.gestionPacientes;
    }

    @Test
    void registraUnPacienteNormalizandoLosDatos() {
        Paciente nuevo = new Paciente("  Carla Lopez ", " 35444555 ");
        Paciente guardado = gestion.registrarPaciente(nuevo);

        assertEquals("35444555", guardado.getDocumento());
        assertEquals("Carla Lopez", guardado.getNombre());
        assertTrue(guardado.isActivo());
    }

    @Test
    void validaLosDatosObligatoriosYElDuplicado() {
        assertThrows(ReglaNegocioException.class, () -> gestion.registrarPaciente(null));
        assertThrows(ReglaNegocioException.class, () -> gestion.registrarPaciente(new Paciente("Sin Documento", " ")));
        assertThrows(ReglaNegocioException.class, () -> gestion.registrarPaciente(new Paciente(" ", "123")));
        assertThrows(ReglaNegocioException.class, () -> gestion.registrarPaciente(new Paciente("Otra Ana", "30111222")));
    }

    @Test
    void buscaPorTextoYPorDocumento() {
        assertEquals(2, gestion.buscar(null).size());
        assertEquals(1, gestion.buscar("bruno").size());
        assertEquals("Ana Gomez", gestion.buscarPorDocumento(" 30111222 ").getNombre());
        assertThrows(NoEncontradoException.class, () -> gestion.buscarPorDocumento("1"));
        assertThrows(ReglaNegocioException.class, () -> gestion.buscarPorDocumento(""));
    }

    @Test
    void actualizaPeroNoDejaCambiarElDocumento() {
        Paciente datos = new Paciente("Ana Maria Gomez", "30111222");
        datos.setEmail("ana@mail.com");
        datos.setActivo(true);
        assertEquals("Ana Maria Gomez", gestion.actualizarDatos("30111222", datos).getNombre());

        assertThrows(ReglaNegocioException.class,
                () -> gestion.actualizarDatos("30111222", new Paciente("Ana", "99999999")));
        assertThrows(ReglaNegocioException.class, () -> gestion.actualizarDatos("30111222", new Paciente(" ", null)));
        assertThrows(ReglaNegocioException.class, () -> gestion.actualizarDatos("30111222", null));
    }

    @Test
    void unPacienteSoloVeSuHistorialYSuFicha() {
        escenario.comoUsuario("paciente", Roles.PACIENTE);

        assertEquals(0, gestion.historialDeTurnos("30111222").size());
        assertThrows(ReglaNegocioException.class, () -> gestion.historialDeTurnos("28999888"));
        assertEquals("30111222", gestion.miFicha().getDocumento());

        escenario.comoUsuario("sin-ficha", Roles.PACIENTE);
        assertThrows(NoEncontradoException.class, () -> gestion.miFicha());
    }

    @Test
    void recepcionPuedeVerCualquierHistorial() {
        assertEquals(0, gestion.historialDeTurnos("28999888").size());
    }
}
