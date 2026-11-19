package com.mediconecta.negocio;

import com.mediconecta.datos.RepositorioIdempotencia;

import jakarta.ejb.Schedule;
import jakarta.ejb.Stateless;
import jakarta.inject.Inject;

import java.time.LocalDateTime;
import java.util.logging.Logger;

/**
 * Borra una vez por hora las {@code Idempotency-Key} vencidas (mas de 24 h).
 *
 * <p>Timer automatico del contenedor ({@code @Schedule}, no persistente: si el
 * servidor esta apagado a la hora en punto no hace falta "recuperar" la
 * ejecucion, la proxima limpia todo igual). Las claves vencidas tampoco se
 * usan aunque sigan en la tabla: {@link FachadaReservas} las trata como nuevas.</p>
 */
@Stateless
public class LimpiezaClavesIdempotencia {

    private static final Logger LOG = Logger.getLogger(LimpiezaClavesIdempotencia.class.getName());

    @Inject
    private RepositorioIdempotencia repositorio;

    @Schedule(hour = "*", minute = "17", persistent = false)
    public void limpiar() {
        int borradas = repositorio.borrarAnterioresA(LocalDateTime.now().minus(FachadaReservas.VIGENCIA_CLAVE));
        if (borradas > 0) {
            LOG.info(() -> "[IDEMPOTENCIA] se borraron " + borradas + " claves vencidas");
        }
    }
}
