package com.mediconecta.datos;

import com.mediconecta.modelo.RegistroIdempotencia;

import java.time.LocalDateTime;
import java.util.Optional;

/** Acceso a las {@code Idempotency-Key} ya usadas (patron DAO / Repository). */
public interface RepositorioIdempotencia {

    Optional<RegistroIdempotencia> buscar(String usuario, String clave);

    /**
     * Guarda una clave nueva y la escribe en la base en el acto.
     *
     * @return {@code false} si otro pedido ya registro la misma clave para el
     *         mismo usuario (la restriccion unica la rechazo)
     */
    boolean registrar(RegistroIdempotencia registro);

    RegistroIdempotencia actualizar(RegistroIdempotencia registro);

    /** Borra las claves anteriores al limite; devuelve cuantas borro. */
    int borrarAnterioresA(LocalDateTime limite);
}
