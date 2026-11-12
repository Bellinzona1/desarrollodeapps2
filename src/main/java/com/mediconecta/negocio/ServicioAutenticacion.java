package com.mediconecta.negocio;

import com.mediconecta.datos.RepositorioUsuarios;
import com.mediconecta.modelo.Usuario;
import com.mediconecta.seguridad.HashDeClaves;

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

    /**
     * @return el usuario autenticado, con sus roles
     * @throws CredencialesInvalidasException si el usuario no existe, esta
     *                                        inactivo o la contrasena no coincide
     */
    @PermitAll
    public Usuario autenticar(String usuario, String clave) {
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

    private static final String HASH_FICTICIO = HashDeClaves.calcular("clave-ficticia-de-tiempo");
}
