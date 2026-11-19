package com.mediconecta.negocio;

import com.mediconecta.modelo.Paciente;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Reglas del padron de pacientes y controles de BOLA/IDOR sobre las fichas. */
class GestionPacientesTest {

    private Escenario e;

    @BeforeEach
    void preparar() {
        e = new Escenario();
    }

    private Paciente datos(String nombre, String documento) {
        Paciente paciente = new Paciente(nombre, documento);
        paciente.setEmail("x@mail.com");
        paciente.setActivo(true);
        return paciente;
    }

    @Test
    void buscaPorTextoOListaTodos() {
        assertEquals(2, e.gestionPacientes.buscar(null).size());
        assertEquals(2, e.gestionPacientes.buscar("  ").size());
        assertEquals(1, e.gestionPacientes.buscar("ana").size());
        assertEquals(2, e.gestionPacientes.listarPacientes().size());
    }

    @Test
    void buscarPorDocumentoValidaYAvisaSiNoExiste() {
        assertEquals("Ana Gomez", e.gestionPacientes.buscarPorDocumento(" 30111222 ").getNombre());
        assertThrows(ReglaNegocioException.class, () -> e.gestionPacientes.buscarPorDocumento(null));
        assertThrows(ReglaNegocioException.class, () -> e.gestionPacientes.buscarPorDocumento(" "));
        assertThrows(NoEncontradoException.class, () -> e.gestionPacientes.buscarPorDocumento("1"));
    }

    @Test
    void unPacienteSoloPuedeVerSuPropiaFicha() {
        e.comoPaciente();
        assertEquals("30111222", e.gestionPacientes.buscarPorDocumento("30111222").getDocumento());
        assertThrows(AccesoDenegadoException.class, () -> e.gestionPacientes.buscarPorDocumento("28999888"));
        // Tampoco puede averiguar si un documento existe: 403 y no 404.
        assertThrows(AccesoDenegadoException.class, () -> e.gestionPacientes.buscarPorDocumento("99999999"));
    }

    @Test
    void elHistorialRespetaElOwnership() {
        e.fachada.reservar(e.solicitudParaAna(Escenario.proximoHabil(10, 0)));
        e.comoPaciente();
        assertEquals(1, e.gestionPacientes.historialDeTurnos("30111222").size());
        assertThrows(AccesoDenegadoException.class, () -> e.gestionPacientes.historialDeTurnos("28999888"));
        e.como("medico", Roles.MEDICO);
        assertEquals(0, e.gestionPacientes.historialDeTurnos("28999888").size());
    }

    @Test
    void exigirPropioDocumentoNoRestringeALosOtrosRoles() {
        e.como("admin", Roles.ADMIN, Roles.PACIENTE);
        assertDoesNotThrow(() -> e.gestionPacientes.exigirPropioDocumentoSiEsPaciente("28999888"));
        e.comoPaciente();
        assertDoesNotThrow(() -> e.gestionPacientes.exigirPropioDocumentoSiEsPaciente("30111222"));
        assertThrows(AccesoDenegadoException.class,
                () -> e.gestionPacientes.exigirPropioDocumentoSiEsPaciente("28999888"));
    }

    @Test
    void miFichaDevuelveLaDelUsuarioOAvisaQueNoTiene() {
        e.comoPaciente();
        assertEquals("30111222", e.gestionPacientes.miFicha().getDocumento());
        e.como("recepcion", Roles.RECEPCION);
        assertThrows(NoEncontradoException.class, () -> e.gestionPacientes.miFicha());
    }

    @Test
    void registraUnPacienteNormalizandoLosDatos() {
        Paciente nuevo = datos("  Eva Perez ", " 40123456 ");
        nuevo.setId(99L);
        nuevo.setActivo(false);
        Paciente creado = e.gestionPacientes.registrarPaciente(nuevo);
        assertNotNull(creado.getId());
        assertEquals("Eva Perez", creado.getNombre());
        assertEquals("40123456", creado.getDocumento());
        assertTrue(creado.isActivo());
    }

    @Test
    void elAltaValidaLosDatosObligatoriosYElDocumentoUnico() {
        assertThrows(ReglaNegocioException.class, () -> e.gestionPacientes.registrarPaciente(null));
        assertThrows(ReglaNegocioException.class, () -> e.gestionPacientes.registrarPaciente(datos("Eva", " ")));
        assertThrows(ReglaNegocioException.class, () -> e.gestionPacientes.registrarPaciente(datos(null, "40")));
        ReglaNegocioException duplicado = assertThrows(ReglaNegocioException.class,
                () -> e.gestionPacientes.registrarPaciente(datos("Otra Ana", "30111222")));
        assertTrue(duplicado.getMessage().contains("30111222"));
    }

    @Test
    void elDocumentoEsUnNumeroDeSeisADiezDigitos() {
        for (String malo : new String[]{"12345", "{abc}12", "30111222\"><x", "30 111 222", "30.111.222",
                "../../etc", "pacientes", "12345678901"}) {
            assertThrows(ReglaNegocioException.class,
                    () -> e.gestionPacientes.registrarPaciente(datos("Eva Perez", malo)), malo);
        }
        assertEquals(2, e.gestionPacientes.listarPacientes().size(), "ningun documento invalido quedo en el padron");
        assertEquals("123456", e.gestionPacientes.registrarPaciente(datos("Eva", "123456")).getDocumento());
        assertEquals("1234567890", e.gestionPacientes.registrarPaciente(datos("Leo", " 1234567890 ")).getDocumento());
    }

    @Test
    void actualizaLosDatosDeContactoPeroNoElDocumento() {
        Paciente cambios = datos(" Ana G. ", null);
        cambios.setTelefono("11-1111-2222");
        cambios.setCobertura("PAMI");
        Paciente actualizado = e.gestionPacientes.actualizarDatos("30111222", cambios);
        assertEquals("Ana G.", actualizado.getNombre());
        assertEquals("PAMI", actualizado.getCobertura());

        assertDoesNotThrow(() -> e.gestionPacientes.actualizarDatos("30111222", datos("Ana", "30111222")));
        assertThrows(ReglaNegocioException.class,
                () -> e.gestionPacientes.actualizarDatos("30111222", datos("Ana", "11111111")));
        assertThrows(ReglaNegocioException.class, () -> e.gestionPacientes.actualizarDatos("30111222", null));
        assertThrows(ReglaNegocioException.class,
                () -> e.gestionPacientes.actualizarDatos("30111222", datos(" ", null)));
        assertThrows(NoEncontradoException.class,
                () -> e.gestionPacientes.actualizarDatos("1", datos("X", null)));
    }

    @Test
    void desactivarUnPaciente() {
        Paciente cambios = datos("Bruno Diaz", null);
        cambios.setActivo(false);
        assertFalse(e.gestionPacientes.actualizarDatos("28999888", cambios).isActivo());
    }
}
