package com.mediconecta.modelo;

import com.mediconecta.modelo.estado.EstadoDelTurno;
import com.mediconecta.modelo.estado.TurnoAtendido;
import com.mediconecta.modelo.estado.TurnoCancelado;
import com.mediconecta.modelo.estado.TurnoProgramado;

/**
 * Estados por los que puede pasar un turno.
 *
 * <p>Es lo que se persiste (columna {@code estado}, como texto) y lo que viaja
 * en el JSON. El comportamiento de cada estado no vive aca sino en su objeto
 * {@link EstadoDelTurno} (patron State): este enum solo asocia cada valor con
 * la clase que sabe que transiciones admite.</p>
 *
 * <pre>
 *   PROGRAMADO --cancelar--> CANCELADO --archivar--> CANCELADO (archivado)
 *       |
 *       +------atender-----> ATENDIDO
 * </pre>
 *
 * <p>El estado lo fija siempre el negocio: un turno nace {@link #PROGRAMADO}
 * en {@code FabricaTurnos} y solo cambia a traves de los metodos de
 * {@link Turno} que delegan en su estado.</p>
 */
public enum EstadoTurno {

    PROGRAMADO(new TurnoProgramado()),

    ATENDIDO(new TurnoAtendido()),

    CANCELADO(new TurnoCancelado());

    private final EstadoDelTurno comportamiento;

    EstadoTurno(EstadoDelTurno comportamiento) {
        this.comportamiento = comportamiento;
    }

    /** Objeto State que implementa las transiciones de este estado. */
    public EstadoDelTurno comportamiento() {
        return comportamiento;
    }
}
