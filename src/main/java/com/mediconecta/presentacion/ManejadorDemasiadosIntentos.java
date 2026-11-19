package com.mediconecta.presentacion;

import com.mediconecta.negocio.DemasiadosIntentosException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Traduce el limite de intentos superado a {@code 429 Too Many Requests} con
 * la cabecera {@code Retry-After} (segundos), como pide la RFC 6585.
 */
@Provider
public class ManejadorDemasiadosIntentos implements ExceptionMapper<DemasiadosIntentosException> {

    @Context
    private UriInfo uri;

    @Override
    public Response toResponse(DemasiadosIntentosException excepcion) {
        return Problemas.respuesta(429, "demasiados-intentos", excepcion.getMessage(), uri)
                .header("Retry-After", excepcion.getSegundosDeEspera())
                .build();
    }
}
