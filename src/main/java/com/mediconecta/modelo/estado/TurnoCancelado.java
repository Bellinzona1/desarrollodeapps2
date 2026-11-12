package com.mediconecta.modelo.estado;

import com.mediconecta.modelo.EstadoTurno;
import com.mediconecta.modelo.Turno;

/**
 * Turno cancelado: libera el horario y lo unico que admite es archivarse
 * (una sola vez).
 */
public final class TurnoCancelado implements EstadoDelTurno {

    @Override
    public EstadoTurno valor() {
        return EstadoTurno.CANCELADO;
    }

    @Override
    public void cancelar(Turno turno) {
        throw new TransicionInvalidaException(
                "El turno " + TransicionInvalidaException.idDe(turno) + " ya estaba cancelado.",
                valor(), "cancelar");
    }

    @Override
    public void atender(Turno turno) {
        throw new TransicionInvalidaException(
                "No se puede atender un turno cancelado.", valor(), "atender");
    }

    @Override
    public void archivar(Turno turno) {
        if (turno.isArchivado()) {
            throw new TransicionInvalidaException(
                    "El turno " + TransicionInvalidaException.idDe(turno) + " ya estaba archivado.",
                    valor(), "archivar");
        }
        turno.setArchivado(true);
    }

    @Override
    public boolean ocupaHorario() {
        return false;
    }
}
