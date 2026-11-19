package com.mediconecta.mensajeria;

import com.mediconecta.datos.RepositorioMensajesProcesados;
import com.mediconecta.modelo.MensajeProcesado;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.LocalDateTime;
import java.util.logging.Logger;

/**
 * Garantiza que cada consumidor aplique el efecto de un mensaje <b>una sola
 * vez</b>, aunque el broker lo entregue varias (entrega "al menos una vez").
 *
 * <p>Lo usan los MDB dentro de su transaccion (CMT, {@code REQUIRED}): el
 * registro en {@code mensajes_procesados}, el efecto y el acuse de recibo al
 * broker se confirman juntos. Si algo falla, se deshace todo y el broker
 * reentrega; si el mensaje ya estaba registrado, se descarta sin efecto.</p>
 */
@ApplicationScoped
public class ConsumidorIdempotente {

    private static final Logger LOG = Logger.getLogger(ConsumidorIdempotente.class.getName());

    private static final int LARGO_DETALLE = 300;

    @Inject
    private RepositorioMensajesProcesados repositorio;

    /**
     * @param consumidor nombre del consumidor (cada uno lleva su propio registro)
     * @param clave      clave de negocio del mensaje
     * @param detalle    texto a guardar como evidencia
     * @param efecto     lo que el consumidor hace con el mensaje
     * @return {@code true} si se proceso; {@code false} si era un duplicado
     */
    public boolean procesarUnaVez(String consumidor, String clave, String detalle, Runnable efecto) {
        if (repositorio.yaProcesado(consumidor, clave)) {
            LOG.info(() -> "[JMS-IDEMPOTENTE] " + consumidor + " descarta un duplicado de " + clave);
            return false;
        }
        String recortado = detalle == null || detalle.length() <= LARGO_DETALLE
                ? detalle : detalle.substring(0, LARGO_DETALLE);
        if (!repositorio.registrar(new MensajeProcesado(consumidor, clave, LocalDateTime.now(), recortado))) {
            // Otra entrega del mismo mensaje se registro al mismo tiempo. Se hace
            // fallar esta: el broker la reentrega y en ese intento se descarta.
            throw new IllegalStateException("Entrega simultanea de " + clave + " para " + consumidor);
        }
        efecto.run();
        return true;
    }
}
