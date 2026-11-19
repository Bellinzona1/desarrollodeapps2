package com.mediconecta.negocio;

import com.mediconecta.modelo.Turno;

/**
 * Resultado de una reserva hecha con {@code Idempotency-Key}.
 *
 * @param turno    el turno creado (o el que ya se habia creado con esa clave)
 * @param repetida {@code true} si el pedido es un reintento y no se creo nada nuevo
 */
public record ResultadoReserva(Turno turno, boolean repetida) {
}
