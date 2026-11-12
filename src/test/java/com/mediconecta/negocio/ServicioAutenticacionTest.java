package com.mediconecta.negocio;

import com.mediconecta.datos.memoria.RepositorioUsuariosEnMemoria;
import com.mediconecta.modelo.Usuario;
import com.mediconecta.seguridad.HashDeClaves;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ServicioAutenticacionTest {

    private ServicioAutenticacion servicio;

    private RepositorioUsuariosEnMemoria repositorio;

    @BeforeEach
    void preparar() {
        repositorio = new RepositorioUsuariosEnMemoria();
        repositorio.guardar(new Usuario("recepcion", HashDeClaves.calcular("Demo.2026"), Set.of(Roles.RECEPCION)));
        servicio = new ServicioAutenticacion();
        Inyeccion.campo(servicio, "repositorio", repositorio);
    }

    @Test
    void conLasCredencialesCorrectasDevuelveElUsuarioConSusRoles() {
        Usuario usuario = servicio.autenticar("recepcion", "Demo.2026");
        assertEquals("recepcion", usuario.getUsuario());
        assertEquals(Set.of(Roles.RECEPCION), usuario.getRoles());
    }

    @Test
    void conLaClaveIncorrectaSeRechaza() {
        assertThrows(CredencialesInvalidasException.class, () -> servicio.autenticar("recepcion", "otra"));
    }

    @Test
    void unUsuarioInexistenteRecibeElMismoRechazo() {
        CredencialesInvalidasException inexistente = assertThrows(CredencialesInvalidasException.class,
                () -> servicio.autenticar("nadie", "Demo.2026"));
        CredencialesInvalidasException claveMala = assertThrows(CredencialesInvalidasException.class,
                () -> servicio.autenticar("recepcion", "otra"));
        assertEquals(claveMala.getMessage(), inexistente.getMessage());
    }

    @Test
    void unUsuarioInactivoNoPuedeEntrar() {
        repositorio.buscarPorUsuario("recepcion").orElseThrow().setActivo(false);
        assertThrows(CredencialesInvalidasException.class, () -> servicio.autenticar("recepcion", "Demo.2026"));
    }

    @Test
    void sinDatosSeRechaza() {
        assertThrows(CredencialesInvalidasException.class, () -> servicio.autenticar(null, null));
        assertThrows(CredencialesInvalidasException.class, () -> servicio.autenticar(" ", "x"));
    }
}
