package com.mediconecta.negocio;

import com.mediconecta.datos.RepositorioUsuarios;
import com.mediconecta.modelo.Usuario;
import com.mediconecta.seguridad.HashDeClaves;
import com.mediconecta.seguridad.ProteccionAbuso;

import jakarta.annotation.security.PermitAll;
import jakarta.ejb.Stateless;
import jakarta.inject.Inject;

import java.util.Optional;

/**
 * Verifica credenciales. EJB <b>stateless</b>.
 *
 * <p>Es el unico metodo de negocio sin rol exigido ({@code @PermitAll}): lo
 * invoca alguien que todavia no inicio sesion. No emite el token: eso es un
 * detalle del transporte HTTP y lo hace la presentacion con
 * {@code seguridad.ServicioJwt}. Asi el negocio sigue sin saber nada de HTTP ni
 * de JWT.</p>
 */
@Stateless
public class ServicioAutenticacion {

    @Inject
    private RepositorioUsuarios repositorio;

    @Inject
    private ProteccionAbuso proteccion;

    /**
     * @return el usuario autenticado, con sus roles
     * @throws CredencialesInvalidasException si el usuario no existe, esta
     *                                        inactivo o la contrasena no coincide
     */
    @PermitAll
    public Usuario autenticar(String usuario, String clave) {
        return autenticar(usuario, clave, null);
    }

    /**
     * Igual que {@link #autenticar(String, String)}, con proteccion contra
     * fuerza bruta: despues de varios fallos seguidos para la misma cuenta o
     * desde el mismo origen, rechaza los intentos con
     * {@link DemasiadosIntentosException} (429) <b>sin verificar la
     * contrasena</b>, asi el atacante no puede seguir probando aunque acierte.
     *
     * @param origen direccion IP del cliente, o {@code null} si no se conoce
     */
    @PermitAll
    public Usuario autenticar(String usuario, String clave, String origen) {
        long espera = proteccion.bloqueoDeLogin(usuario, origen);
        if (espera > 0) {
            throw new DemasiadosIntentosException(
                    "Demasiados intentos fallidos. Espere " + minutos(espera) + " antes de volver a intentar.", espera);
        }
        try {
            Usuario cuenta = verificar(usuario, clave);
            proteccion.loginCorrecto(usuario);
            return cuenta;
        } catch (CredencialesInvalidasException fallo) {
            proteccion.loginFallido(usuario, origen);
            throw fallo;
        }
    }

    private Usuario verificar(String usuario, String clave) {
        if (usuario == null || usuario.isBlank() || clave == null || clave.isEmpty()) {
            throw new CredencialesInvalidasException();
        }
        Optional<Usuario> encontrado = repositorio.buscarPorUsuario(usuario.trim());
        if (encontrado.isEmpty()) {
            // Se calcula igual un hash para que el tiempo de respuesta no
            // delate si el usuario existe o no.
            HashDeClaves.coincide(clave, HASH_FICTICIO);
            throw new CredencialesInvalidasException();
        }
        Usuario cuenta = encontrado.get();
        if (!cuenta.isActivo() || !HashDeClaves.coincide(clave, cuenta.getHashClave())) {
            throw new CredencialesInvalidasException();
        }
        return cuenta;
    }

    private static String minutos(long segundos) {
        long minutos = (segundos + 59) / 60;
        return minutos == 1 ? "1 minuto" : minutos + " minutos";
    }

    private static final String HASH_FICTICIO = HashDeClaves.calcular("clave-ficticia-de-tiempo");
}
