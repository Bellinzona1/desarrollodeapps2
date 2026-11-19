package com.mediconecta.datos.jpa;

import com.mediconecta.datos.RepositorioMensajesProcesados;
import com.mediconecta.modelo.MensajeProcesado;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.PersistenceException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Implementacion de {@link RepositorioMensajesProcesados} sobre JPA + PostgreSQL.
 *
 * <p>Corre dentro de la transaccion del MDB que lo invoca: el registro del
 * mensaje y el acuse de recibo del broker se confirman o se deshacen juntos.</p>
 */
@ApplicationScoped
public class RepositorioMensajesProcesadosJpa implements RepositorioMensajesProcesados {

    @PersistenceContext(unitName = "mediconectaPU")
    private EntityManager em;

    @Override
    public boolean yaProcesado(String consumidor, String clave) {
        return em.createQuery("select count(m) from MensajeProcesado m"
                        + " where m.consumidor = :consumidor and m.clave = :clave", Long.class)
                .setParameter("consumidor", consumidor)
                .setParameter("clave", clave)
                .getSingleResult() > 0;
    }

    @Override
    public boolean registrar(MensajeProcesado mensaje) {
        try {
            em.persist(mensaje);
            em.flush();
            return true;
        } catch (PersistenceException duplicado) {
            return false;
        }
    }

    @Override
    public Map<String, Long> contarPorConsumidor() {
        Map<String, Long> cantidades = new LinkedHashMap<>();
        for (Object[] fila : em.createQuery("select m.consumidor, count(m) from MensajeProcesado m"
                + " group by m.consumidor order by m.consumidor", Object[].class).getResultList()) {
            cantidades.put((String) fila[0], (Long) fila[1]);
        }
        return cantidades;
    }

    @Override
    public List<MensajeProcesado> ultimos(String consumidor, int cantidad) {
        return em.createQuery("select m from MensajeProcesado m where m.consumidor = :consumidor"
                        + " order by m.procesadoEn desc, m.id desc", MensajeProcesado.class)
                .setParameter("consumidor", consumidor)
                .setMaxResults(cantidad)
                .getResultList();
    }
}
