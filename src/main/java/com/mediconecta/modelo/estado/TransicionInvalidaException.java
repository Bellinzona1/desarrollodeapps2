package com.mediconecta.modelo.estado;

import com.mediconecta.modelo.EstadoTurno;
import com.mediconecta.modelo.Turno;

import jakarta.ejb.ApplicationException;

/**
 * Se pidio una transicion que el estado actual del turno no admite.
 *
 * <p>La presentacion la traduce a <b>409 Conflict</b> (y no a 400): el pedido
 * es valido en si mismo, lo que falla es que choca con el estado actual del
 * recurso. Cancelar un turno es una operacion correcta; cancelar uno que ya
 * esta cancelado es un conflicto.</p>
 *
 * <p>Igual que las demas excepciones del dominio, es un resultado previsto y no
 * una falla de sistema: {@code rollback = false}.</p>
 */
@ApplicationException(rollback = false)
public class TransicionInvalidaException extends RuntimeException {

    private final EstadoTurno estadoActual;

    private final String operacion;

    public TransicionInvalidaException(String mensaje, EstadoTurno estadoActual, String operacion) {
        super(mensaje);
        this.estadoActual = estadoActual;
        this.operacion = operacion;
    }

    /** Mensaje generico para una transicion que el estado no sobreescribio. */
    static TransicionInvalidaException de(Turno turno, EstadoTurno estado, String operacion) {
        return new TransicionInvalidaException(
                "No se puede " + operacion + " el turno " + idDe(turno)
                        + " porque esta " + estado.name() + ".",
                estado, operacion);
    }

    static String idDe(Turno turno) {
        return turno.getId() == null ? "(nuevo)" : String.valueOf(turno.getId());
    }

    public EstadoTurno getEstadoActual() {
        return estadoActual;
    }

    public String getOperacion() {
        return operacion;
    }
}
