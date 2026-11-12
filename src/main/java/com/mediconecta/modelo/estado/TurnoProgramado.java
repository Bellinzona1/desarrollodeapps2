package com.mediconecta.modelo.estado;

import com.mediconecta.modelo.EstadoTurno;
import com.mediconecta.modelo.Turno;

/** Estado inicial: el turno se puede cancelar o marcar como atendido. */
public final class TurnoProgramado implements EstadoDelTurno {

    @Override
    public EstadoTurno valor() {
        return EstadoTurno.PROGRAMADO;
    }

    @Override
    public void cancelar(Turno turno) {
        turno.setEstado(EstadoTurno.CANCELADO);
    }

    @Override
    public void atender(Turno turno) {
        turno.setEstado(EstadoTurno.ATENDIDO);
    }

    @Override
    public void archivar(Turno turno) {
        throw new TransicionInvalidaException(
                "Solo se pueden archivar turnos cancelados.", valor(), "archivar");
    }
}
