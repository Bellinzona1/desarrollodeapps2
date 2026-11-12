package com.mediconecta.datos;

import com.mediconecta.modelo.Usuario;

import java.util.Optional;

/** Contrato de acceso a las cuentas de usuario (patron DAO / Repository). */
public interface RepositorioUsuarios {

    Optional<Usuario> buscarPorUsuario(String usuario);

    long contar();

    Usuario guardar(Usuario usuario);
}
