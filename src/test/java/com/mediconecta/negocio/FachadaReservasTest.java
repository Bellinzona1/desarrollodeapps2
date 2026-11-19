package com.mediconecta.negocio;

import com.mediconecta.modelo.Paciente;
import com.mediconecta.modelo.RegistroIdempotencia;
import com.mediconecta.modelo.Turno;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Caso de uso "reservar" de punta a punta en la fachada, incluida la Idempotency-Key. */
class FachadaReservasTest {

    private Escenario e;

    private String horario;

    @BeforeEach
    void preparar() {
        e = new Escenario();
        horario = Escenario.proximoHabil(10, 0);
    }

    // ---------------------------------------------------------------- reservar

    @Test
    void reservaUnTurnoYAvisaPorJms() {
        Turno turno = e.fachada.reservar(e.solicitudParaAna(horario));
        assertEquals("30111222", turno.getPaciente().getDocumento());
        assertEquals(1, e.notificador.enviados.size());
        assertSame(turno, e.notificador.enviados.get(0));
    }

    @Test
    void sinDatosNoHayReserva() {
        assertThrows(ReglaNegocioException.class, () -> e.fachada.reservar(null));
        SolicitudTurno sinDocumento = e.solicitudParaAna(horario);
        sinDocumento.setDocumentoPaciente(" ");
        assertThrows(ReglaNegocioException.class, () -> e.fachada.reservar(sinDocumento));
        assertTrue(e.notificador.enviados.isEmpty());
    }

    @Test
    void unPacienteNuevoSeDaDeAltaAlVueloSiVieneConNombre() {
        SolicitudTurno solicitud = e.solicitudParaAna(horario);
        solicitud.setDocumentoPaciente("40123456");
        solicitud.setNombrePaciente("Eva Perez");
        Turno turno = e.fachada.reservar(solicitud);
        assertEquals("Eva Perez", turno.getPaciente().getNombre());
        assertTrue(e.repoPacientes.buscarPorDocumento("40123456").isPresent());
    }

    @Test
    void unDocumentoDesconocidoSinNombreSeRechaza() {
        SolicitudTurno solicitud = e.solicitudParaAna(horario);
        solicitud.setDocumentoPaciente("40123456");
        ReglaNegocioException error = assertThrows(ReglaNegocioException.class, () -> e.fachada.reservar(solicitud));
        assertTrue(error.getMessage().contains("40123456"));
    }

    @Test
    void elProfesionalSePuedeIndicarPorNombre() {
        SolicitudTurno solicitud = e.solicitudParaAna(horario);
        solicitud.setProfesionalId(null);
        solicitud.setNombreProfesional(" dra. laura fernandez ");
        assertEquals(e.laura.getId(), e.fachada.reservar(solicitud).getProfesional().getId());

        SolicitudTurno inexistente = e.solicitudParaAna(Escenario.proximoHabil(11, 0));
        inexistente.setProfesionalId(null);
        inexistente.setNombreProfesional("Dr. Nadie");
        assertThrows(ReglaNegocioException.class, () -> e.fachada.reservar(inexistente));

        SolicitudTurno sinProfesional = e.solicitudParaAna(Escenario.proximoHabil(11, 30));
        sinProfesional.setProfesionalId(null);
        assertThrows(ReglaNegocioException.class, () -> e.fachada.reservar(sinProfesional));
    }

    @Test
    void unPacienteSoloReservaParaSiMismoYNoDaDeAltaFichasAjenas() {
        e.comoPaciente();
        assertEquals("30111222", e.fachada.reservar(e.solicitudParaAna(horario)).getPaciente().getDocumento());

        SolicitudTurno ajena = e.solicitudParaAna(Escenario.proximoHabil(11, 0));
        ajena.setDocumentoPaciente("28999888");
        assertThrows(AccesoDenegadoException.class, () -> e.fachada.reservar(ajena));

        SolicitudTurno inventada = e.solicitudParaAna(Escenario.proximoHabil(11, 30));
        inventada.setDocumentoPaciente("40999999");
        inventada.setNombrePaciente("Alguien Inventado");
        assertThrows(AccesoDenegadoException.class, () -> e.fachada.reservar(inventada));
        assertFalse(e.repoPacientes.buscarPorDocumento("40999999").isPresent(),
                "un PACIENTE no debe poder dar de alta fichas de otras personas");
    }

    // ------------------------------------------------------- Idempotency-Key

    @Test
    void sinClaveSeComportaComoReservar() {
        ResultadoReserva resultado = e.fachada.reservarIdempotente(e.solicitudParaAna(horario), null);
        assertFalse(resultado.repetida());
        assertEquals(1, e.notificador.enviados.size());
    }

    @Test
    void unReintentoConLaMismaClaveDevuelveElMismoTurnoSinCrearOtro() {
        ResultadoReserva primero = e.fachada.reservarIdempotente(e.solicitudParaAna(horario), "clave-1");
        ResultadoReserva reintento = e.fachada.reservarIdempotente(e.solicitudParaAna(horario), "clave-1");

        assertFalse(primero.repetida());
        assertTrue(reintento.repetida());
        assertEquals(primero.turno().getId(), reintento.turno().getId());
        assertEquals(1, e.repoTurnos.listarTodos().size());
        assertEquals(1, e.notificador.enviados.size(), "el reintento no debe volver a avisar por JMS");
    }

    @Test
    void laClaveSeAceptaComoCadenaDeStructuredFields() {
        ResultadoReserva primero = e.fachada.reservarIdempotente(e.solicitudParaAna(horario), "\"abc-123\"");
        ResultadoReserva reintento = e.fachada.reservarIdempotente(e.solicitudParaAna(horario), "abc-123");
        assertTrue(reintento.repetida());
        assertEquals(primero.turno().getId(), reintento.turno().getId());
    }

    @Test
    void laMismaClaveConOtrosDatosSeRechazaComoReutilizada() {
        e.fachada.reservarIdempotente(e.solicitudParaAna(horario), "clave-1");
        SolicitudTurno otra = e.solicitudParaAna(Escenario.proximoHabil(11, 0));

        IdempotenciaException error = assertThrows(IdempotenciaException.class,
                () -> e.fachada.reservarIdempotente(otra, "clave-1"));
        assertEquals(IdempotenciaException.Motivo.REUTILIZADA, error.getMotivo());
        assertEquals(1, e.repoTurnos.listarTodos().size());
    }

    @Test
    void laClaveEsPorUsuario() {
        e.fachada.reservarIdempotente(e.solicitudParaAna(horario), "compartida");
        e.como("admin", Roles.ADMIN);
        SolicitudTurno otra = e.solicitudParaAna(Escenario.proximoHabil(11, 0));
        assertFalse(e.fachada.reservarIdempotente(otra, "compartida").repetida());
        assertEquals(2, e.repoTurnos.listarTodos().size());
    }

    @Test
    void unaClaveConFormatoInvalidoSeRechaza() {
        assertThrows(ReglaNegocioException.class,
                () -> e.fachada.reservarIdempotente(e.solicitudParaAna(horario), "con espacios"));
        assertThrows(ReglaNegocioException.class,
                () -> e.fachada.reservarIdempotente(e.solicitudParaAna(horario), "x".repeat(256)));
        assertThrows(ReglaNegocioException.class,
                () -> e.fachada.reservarIdempotente(e.solicitudParaAna(horario), "\"\""));
        assertThrows(ReglaNegocioException.class, () -> e.fachada.reservarIdempotente(null, "clave"));
        assertTrue(e.repoTurnos.listarTodos().isEmpty());
    }

    @Test
    void siOtroPedidoConLaMismaClaveEstaEnCursoSeRechazaComoEnCurso() {
        // Simula la carrera: entre la busqueda y el registro, otro pedido registro la clave.
        e.repoIdempotencia.registrar(new RegistroIdempotencia("recepcion", "carrera", "otra-huella",
                LocalDateTime.now()));
        com.mediconecta.datos.RepositorioIdempotencia sinVerAlOtro = new com.mediconecta.datos.RepositorioIdempotencia() {
            @Override
            public java.util.Optional<RegistroIdempotencia> buscar(String usuario, String clave) {
                return java.util.Optional.empty();
            }

            @Override
            public boolean registrar(RegistroIdempotencia registro) {
                return e.repoIdempotencia.registrar(registro);
            }

            @Override
            public RegistroIdempotencia actualizar(RegistroIdempotencia registro) {
                return e.repoIdempotencia.actualizar(registro);
            }

            @Override
            public int borrarAnterioresA(LocalDateTime limite) {
                return 0;
            }
        };
        Inyeccion.campo(e.fachada, "repositorioIdempotencia", sinVerAlOtro);

        IdempotenciaException error = assertThrows(IdempotenciaException.class,
                () -> e.fachada.reservarIdempotente(e.solicitudParaAna(horario), "carrera"));
        assertEquals(IdempotenciaException.Motivo.EN_CURSO, error.getMotivo());
        assertTrue(e.repoTurnos.listarTodos().isEmpty());
    }

    @Test
    void siElPrimerIntentoFalloPorUnaReglaElReintentoConLaMismaClaveVuelveAProbar() {
        SolicitudTurno enElPasado = e.solicitudParaAna(LocalDateTime.now().minusDays(1).format(Turno.FORMATO_FECHA_HORA));
        assertThrows(ReglaNegocioException.class, () -> e.fachada.reservarIdempotente(enElPasado, "k"));
        RegistroIdempotencia registro = e.repoIdempotencia.buscar("recepcion", "k").orElseThrow();
        assertNull(registro.getTurnoId());

        // Mismo pedido, misma clave: vuelve a intentarlo (y vuelve a fallar por la misma regla).
        assertThrows(ReglaNegocioException.class, () -> e.fachada.reservarIdempotente(enElPasado, "k"));
        assertTrue(e.repoTurnos.listarTodos().isEmpty());
    }

    @Test
    void unaClaveVencidaSeTrataComoNueva() {
        ResultadoReserva primero = e.fachada.reservarIdempotente(e.solicitudParaAna(horario), "vieja");
        RegistroIdempotencia registro = e.repoIdempotencia.buscar("recepcion", "vieja").orElseThrow();
        registro.setCreadaEn(LocalDateTime.now().minusHours(25));

        SolicitudTurno otra = e.solicitudParaAna(Escenario.proximoHabil(11, 0));
        ResultadoReserva segundo = e.fachada.reservarIdempotente(otra, "vieja");
        assertFalse(segundo.repetida());
        assertNotEquals(primero.turno().getId(), segundo.turno().getId());
    }

    @Test
    void laHuellaIgnoraEspaciosYDistingueCadaCampo() {
        SolicitudTurno a = e.solicitudParaAna(horario);
        SolicitudTurno b = e.solicitudParaAna(horario);
        b.setMotivo(" Control ");
        assertEquals(FachadaReservas.huella(a), FachadaReservas.huella(b));
        b.setMotivo("Otro motivo");
        assertNotEquals(FachadaReservas.huella(a), FachadaReservas.huella(b));
        assertEquals(64, FachadaReservas.huella(new SolicitudTurno()).length());
    }

    // ------------------------------------------------------------- consultas

    @Test
    void consultasParaLaPantallaDeReserva() {
        assertEquals(2, e.fachada.especialidades().size());
        assertEquals(1, e.fachada.profesionalesDe("Cardiologia").size());
        LocalDateTime momento = LocalDateTime.parse(horario);
        assertTrue(e.fachada.horariosLibres(e.laura.getId(), momento.toLocalDate()).contains(momento));
    }

    @Test
    void elTableroResumeLaAgenda() {
        e.fachada.reservar(e.solicitudParaAna(horario));
        ResumenAgenda resumen = e.fachada.resumenDelDia();
        assertEquals(2, resumen.getPacientesActivos());
        assertEquals(2, resumen.getProfesionalesActivos());
        assertEquals(1, resumen.getPendientes());
        assertEquals(1, resumen.getProximosTurnos().size());
        Paciente inactivo = e.bruno;
        inactivo.setActivo(false);
        assertEquals(1, e.fachada.resumenDelDia().getPacientesActivos());
    }
}
