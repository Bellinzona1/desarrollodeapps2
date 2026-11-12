package com.mediconecta.datos.jpa;

import com.mediconecta.datos.RepositorioUsuarios;
import com.mediconecta.modelo.Usuario;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.util.Optional;

/** Implementacion de {@link RepositorioUsuarios} sobre JPA + PostgreSQL. */
@ApplicationScoped
public class RepositorioUsuariosJpa implements RepositorioUsuarios {

    @PersistenceContext(unitName = "mediconectaPU")
    private EntityManager em;

    @Override
    public Optional<Usuario> buscarPorUsuario(String usuario) {
        if (usuario == null) {
            return Optional.empty();
        }
        return em.createQuery("select u from Usuario u where u.usuario = :usuario", Usuario.class)
                .setParameter("usuario", usuario)
                .getResultStream()
                .findFirst();
    }

    @Override
    public long contar() {
        return em.createQuery("select count(u) from Usuario u", Long.class).getSingleResult();
    }

    @Override
    public Usuario guardar(Usuario usuario) {
        em.persist(usuario);
        em.flush();
        return usuario;
    }
}
