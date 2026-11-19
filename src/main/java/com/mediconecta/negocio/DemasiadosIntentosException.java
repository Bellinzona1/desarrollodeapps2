package com.mediconecta.negocio;

import jakarta.ejb.ApplicationException;

/**
 * Se supero el limite de intentos o de pedidos permitido en una ventana de
 * tiempo (fuerza bruta en el login, abuso del asistente de IA).
 *
 * <p>La presentacion la traduce a {@code 429 Too Many Requests} con la cabecera
 * {@code Retry-After}. El negocio solo informa cuantos segundos hay que esperar;
 * no sabe nada de HTTP.</p>
 */
@ApplicationException(rollback = true)
public class DemasiadosIntentosException extends RuntimeException {

    private final long segundosDeEspera;

    public DemasiadosIntentosException(String mensaje, long segundosDeEspera) {
        super(mensaje);
        this.segundosDeEspera = Math.max(1, segundosDeEspera);
    }

    public long getSegundosDeEspera() {
        return segundosDeEspera;
    }
}
