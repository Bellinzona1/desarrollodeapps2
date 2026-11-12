package com.mediconecta.datos.memoria;

import com.mediconecta.datos.RepositorioUsuarios;
import com.mediconecta.modelo.Usuario;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Implementacion en memoria de {@link RepositorioUsuarios}.
 * Desactivada por defecto: ver {@link RepositorioTurnosEnMemoria}.
 */
@Alternative
@ApplicationScoped
public class RepositorioUsuariosEnMemoria implements RepositorioUsuarios {

    private final Map<String, Usuario> usuarios = new ConcurrentHashMap<>();

    private final AtomicLong generadorDeId = new AtomicLong(0);

    @Override
    public Optional<Usuario> buscarPorUsuario(String usuario) {
        return usuario == null ? Optional.empty() : Optional.ofNullable(usuarios.get(usuario));
    }

    @Override
    public long contar() {
        return usuarios.size();
    }

    @Override
    public Usuario guardar(Usuario usuario) {
        usuario.setId(generadorDeId.incrementAndGet());
        usuarios.put(usuario.getUsuario(), usuario);
        return usuario;
    }
}
