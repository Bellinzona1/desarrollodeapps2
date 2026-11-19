package com.mediconecta.negocio;

import jakarta.ejb.ApplicationException;

/**
 * Problema con la cabecera {@code Idempotency-Key} de un alta.
 *
 * <ul>
 *   <li>{@link Motivo#REUTILIZADA}: la misma clave ya se uso con otro cuerpo
 *       (la presentacion responde {@code 422 Unprocessable Content});</li>
 *   <li>{@link Motivo#EN_CURSO}: otro pedido con la misma clave se esta
 *       procesando en este momento ({@code 409 Conflict}).</li>
 * </ul>
 *
 * <p>Marca la transaccion para rollback: en ninguno de los dos casos se debe
 * crear nada.</p>
 */
@ApplicationException(rollback = true)
public class IdempotenciaException extends RuntimeException {

    /** Por que se rechazo el pedido. */
    public enum Motivo { REUTILIZADA, EN_CURSO }

    private final Motivo motivo;

    public IdempotenciaException(Motivo motivo, String mensaje) {
        super(mensaje);
        this.motivo = motivo;
    }

    public Motivo getMotivo() {
        return motivo;
    }
}
