package com.mediconecta.negocio;

import com.mediconecta.datos.memoria.RepositorioUsuariosEnMemoria;
import com.mediconecta.modelo.Usuario;
import com.mediconecta.seguridad.HashDeClaves;
import com.mediconecta.seguridad.ProteccionAbuso;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
        reloj = new RelojDePrueba();
        Inyeccion.campo(servicio, "proteccion", new ProteccionAbuso(reloj));
    }

    private RelojDePrueba reloj;

    /** Reloj que los tests pueden adelantar. */
    static final class RelojDePrueba extends Clock {
        private Instant ahora = Instant.parse("2026-11-20T12:00:00Z");

        void avanzar(Duration tiempo) {
            ahora = ahora.plus(tiempo);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zona) {
            return this;
        }

        @Override
        public Instant instant() {
            return ahora;
        }
    }

    private void fallar(String usuario, String origen, int veces) {
        for (int i = 0; i < veces; i++) {
            assertThrows(CredencialesInvalidasException.class,
                    () -> servicio.autenticar(usuario, "incorrecta", origen));
        }
    }

    @Test
    void despuesDeCincoFallosLaCuentaSeBloqueaAunqueLaClaveSeaCorrecta() {
        fallar("recepcion", "10.0.0.1", 5);

        DemasiadosIntentosException bloqueo = assertThrows(DemasiadosIntentosException.class,
                () -> servicio.autenticar("recepcion", "Demo.2026", "10.0.0.2"));
        assertTrue(bloqueo.getSegundosDeEspera() > 0 && bloqueo.getSegundosDeEspera() <= 15 * 60);
        assertTrue(bloqueo.getMessage().contains("minutos"));
    }

    @Test
    void elBloqueoEsTemporal() {
        fallar("Recepcion ", "10.0.0.1", 5);
        reloj.avanzar(Duration.ofMinutes(16));
        assertEquals("recepcion", servicio.autenticar("recepcion", "Demo.2026", "10.0.0.1").getUsuario());
    }

    @Test
    void unLoginCorrectoReiniciaElContadorDeLaCuenta() {
        fallar("recepcion", null, 4);
        servicio.autenticar("recepcion", "Demo.2026", null);
        fallar("recepcion", null, 4);
        assertEquals("recepcion", servicio.autenticar("recepcion", "Demo.2026").getUsuario());
    }

    @Test
    void muchosFallosDesdeElMismoOrigenBloqueanEseOrigen() {
        for (int i = 0; i < ProteccionAbuso.FALLOS_POR_ORIGEN; i++) {
            fallar("usuario" + i, "10.9.9.9", 1);
        }
        assertThrows(DemasiadosIntentosException.class,
                () -> servicio.autenticar("recepcion", "Demo.2026", "10.9.9.9"));
        assertFalse(servicio.autenticar("recepcion", "Demo.2026", "10.1.1.1").getRoles().isEmpty());
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
