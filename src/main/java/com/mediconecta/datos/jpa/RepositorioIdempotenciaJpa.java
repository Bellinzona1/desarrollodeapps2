package com.mediconecta.datos.jpa;

import com.mediconecta.datos.RepositorioIdempotencia;
import com.mediconecta.modelo.RegistroIdempotencia;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.PersistenceException;

import java.time.LocalDateTime;
import java.util.Optional;

/** Implementacion de {@link RepositorioIdempotencia} sobre JPA + PostgreSQL. */
@ApplicationScoped
public class RepositorioIdempotenciaJpa implements RepositorioIdempotencia {

    @PersistenceContext(unitName = "mediconectaPU")
    private EntityManager em;

    @Override
    public Optional<RegistroIdempotencia> buscar(String usuario, String clave) {
        return em.createQuery("select r from RegistroIdempotencia r"
                        + " where r.usuario = :usuario and r.clave = :clave", RegistroIdempotencia.class)
                .setParameter("usuario", usuario)
                .setParameter("clave", clave)
                .getResultStream()
                .findFirst();
    }

    /**
     * El {@code flush} inmediato es a proposito: si otro pedido con la misma
     * clave esta en curso, el INSERT espera a que ese termine y despues choca
     * con la restriccion unica. Asi el conflicto aparece aca, antes de crear el
     * turno, y no recien en el commit. La transaccion queda marcada para
     * rollback y el negocio responde con un conflicto.
     */
    @Override
    public boolean registrar(RegistroIdempotencia registro) {
        try {
            em.persist(registro);
            em.flush();
            return true;
        } catch (PersistenceException duplicada) {
            return false;
        }
    }

    @Override
    public RegistroIdempotencia actualizar(RegistroIdempotencia registro) {
        return em.merge(registro);
    }

    @Override
    public int borrarAnterioresA(LocalDateTime limite) {
        return em.createQuery("delete from RegistroIdempotencia r where r.creadaEn < :limite")
                .setParameter("limite", limite)
                .executeUpdate();
    }
}
