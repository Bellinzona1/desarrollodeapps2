package com.mediconecta.negocio;

import com.mediconecta.datos.memoria.RepositorioIdempotenciaEnMemoria;
import com.mediconecta.datos.memoria.RepositorioPacientesEnMemoria;
import com.mediconecta.datos.memoria.RepositorioProfesionalesEnMemoria;
import com.mediconecta.datos.memoria.RepositorioTurnosEnMemoria;
import com.mediconecta.datos.memoria.RepositorioUsuariosEnMemoria;
import com.mediconecta.modelo.RegistroIdempotencia;
import com.mediconecta.seguridad.HashDeClaves;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Piezas de apoyo del negocio: carga demo, fechas, limpieza de claves y excepciones. */
class SoporteNegocioTest {

    @Test
    void laCargaDemoEsIdempotente() {
        RepositorioProfesionalesEnMemoria profesionales = new RepositorioProfesionalesEnMemoria();
        RepositorioPacientesEnMemoria pacientes = new RepositorioPacientesEnMemoria();
        RepositorioTurnosEnMemoria turnos = new RepositorioTurnosEnMemoria();
        RepositorioUsuariosEnMemoria usuarios = new RepositorioUsuariosEnMemoria();
        CargaDeDatosDemo carga = new CargaDeDatosDemo();
        Inyeccion.campo(carga, "repositorioProfesionales", profesionales);
        Inyeccion.campo(carga, "repositorioPacientes", pacientes);
        Inyeccion.campo(carga, "repositorioTurnos", turnos);
        Inyeccion.campo(carga, "repositorioUsuarios", usuarios);
        Inyeccion.campo(carga, "fabricaTurnos", new FabricaTurnos());

        carga.cargar();
        assertEquals(4, profesionales.listarTodos().size());
        assertEquals(4, pacientes.listarTodos().size());
        assertEquals(3, turnos.listarTodos().size());
        assertEquals(4, usuarios.contar());
        assertTrue(HashDeClaves.coincide(CargaDeDatosDemo.CLAVE_DEMO,
                usuarios.buscarPorUsuario("admin").orElseThrow().getHashClave()));

        carga.cargar();
        assertEquals(4, profesionales.listarTodos().size());
        assertEquals(3, turnos.listarTodos().size());
        assertEquals(4, usuarios.contar());
    }

    @Test
    void fechasInterpretaElFormatoDeLaApi() {
        assertEquals(LocalDateTime.of(2026, 11, 20, 9, 30), Fechas.momento("2026-11-20T09:30"));
        assertEquals(LocalDate.of(2026, 11, 20), Fechas.dia("2026-11-20"));
        assertThrows(ReglaNegocioException.class, () -> Fechas.momento("20/11/2026"));
        assertThrows(ReglaNegocioException.class, () -> Fechas.dia("manana"));
    }

    @Test
    void laLimpiezaBorraSoloLasClavesVencidas() {
        RepositorioIdempotenciaEnMemoria repositorio = new RepositorioIdempotenciaEnMemoria();
        repositorio.registrar(new RegistroIdempotencia("u", "vieja", "h", LocalDateTime.now().minusHours(30)));
        repositorio.registrar(new RegistroIdempotencia("u", "nueva", "h", LocalDateTime.now()));
        LimpiezaClavesIdempotencia limpieza = new LimpiezaClavesIdempotencia();
        Inyeccion.campo(limpieza, "repositorio", repositorio);

        limpieza.limpiar();
        assertFalse(repositorio.buscar("u", "vieja").isPresent());
        assertTrue(repositorio.buscar("u", "nueva").isPresent());
        limpieza.limpiar();
        assertTrue(repositorio.buscar("u", "nueva").isPresent());
    }

    @Test
    void elRepositorioDeClavesRechazaDuplicadosComoLaRestriccionUnica() {
        RepositorioIdempotenciaEnMemoria repositorio = new RepositorioIdempotenciaEnMemoria();
        assertTrue(repositorio.registrar(new RegistroIdempotencia("u", "k", "h", LocalDateTime.now())));
        assertFalse(repositorio.registrar(new RegistroIdempotencia("u", "k", "otra", LocalDateTime.now())));
        assertTrue(repositorio.registrar(new RegistroIdempotencia("v", "k", "h", LocalDateTime.now())));
        assertEquals("h", repositorio.buscar("u", "k").orElseThrow().getHuella());
    }

    @Test
    void excepcionesDeNegocio() {
        assertEquals(1, new DemasiadosIntentosException("x", 0).getSegundosDeEspera());
        assertEquals(30, new DemasiadosIntentosException("x", 30).getSegundosDeEspera());
        assertTrue(new AccesoDenegadoException("x") instanceof ReglaNegocioException);
        assertEquals(IdempotenciaException.Motivo.EN_CURSO,
                new IdempotenciaException(IdempotenciaException.Motivo.EN_CURSO, "x").getMotivo());
        assertTrue(NoEncontradoException.turno(7L).getMessage().contains("7"));
        assertTrue(NoEncontradoException.paciente("123").getMessage().contains("123"));
        assertTrue(NoEncontradoException.profesional(9L).getMessage().contains("9"));
        assertNull(new IntegracionExternaException("x").getCause());
    }
}
