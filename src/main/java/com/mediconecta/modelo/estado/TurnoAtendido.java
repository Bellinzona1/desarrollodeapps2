package com.mediconecta.modelo.estado;

import com.mediconecta.modelo.EstadoTurno;
import com.mediconecta.modelo.Turno;

/** Estado final: la atencion ya ocurrio y el turno no admite mas cambios. */
public final class TurnoAtendido implements EstadoDelTurno {

    @Override
    public EstadoTurno valor() {
        return EstadoTurno.ATENDIDO;
    }

    @Override
    public void cancelar(Turno turno) {
        throw new TransicionInvalidaException(
                "No se puede cancelar un turno que ya fue atendido.", valor(), "cancelar");
    }

    @Override
    public void atender(Turno turno) {
        throw new TransicionInvalidaException(
                "El turno " + TransicionInvalidaException.idDe(turno) + " ya fue atendido.",
                valor(), "atender");
    }
}
