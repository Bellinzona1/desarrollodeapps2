package com.mediconecta.modelo.estado;

import com.mediconecta.modelo.EstadoTurno;
import com.mediconecta.modelo.Turno;

/**
 * Patron <b>State</b>: comportamiento de un {@link Turno} segun el estado en
 * que se encuentra.
 *
 * <p><b>Problema que resuelve.</b> Que operaciones admite un turno depende de
 * su estado: uno PROGRAMADO se puede cancelar o atender; uno CANCELADO solo se
 * puede archivar; uno ATENDIDO ya no admite cambios. Antes esas reglas eran una
 * cadena de {@code if (estado == ...)} repetida en cada metodo de
 * {@code GestionTurnos}; cada estado nuevo obligaba a revisar todos los
 * {@code if}.</p>
 *
 * <p><b>Como lo resuelve.</b> Cada estado es una clase que implementa esta
 * interfaz y decide por si misma que transiciones acepta. El turno (el
 * <i>contexto</i> del patron) delega en su estado actual. Por omision toda
 * transicion es invalida: un estado solo sobreescribe las que acepta, asi que
 * olvidarse de una regla nunca habilita una transicion por accidente.</p>
 *
 * <p>Una transicion invalida lanza {@link TransicionInvalidaException}, que la
 * presentacion traduce a <b>409 Conflict</b>: el pedido esta bien formado pero
 * choca con el estado actual del recurso.</p>
 */
public interface EstadoDelTurno {

    /** Valor persistido que representa este estado. */
    EstadoTurno valor();

    /** PROGRAMADO -> CANCELADO. */
    default void cancelar(Turno turno) {
        throw TransicionInvalidaException.de(turno, valor(), "cancelar");
    }

    /** PROGRAMADO -> ATENDIDO. */
    default void atender(Turno turno) {
        throw TransicionInvalidaException.de(turno, valor(), "atender");
    }

    /** CANCELADO -> CANCELADO + archivado (sale de la agenda, queda en el historial). */
    default void archivar(Turno turno) {
        throw TransicionInvalidaException.de(turno, valor(), "archivar");
    }

    /** Si un turno en este estado bloquea el horario del profesional. */
    default boolean ocupaHorario() {
        return true;
    }
}
