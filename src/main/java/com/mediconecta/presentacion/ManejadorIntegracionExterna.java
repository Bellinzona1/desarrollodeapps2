package com.mediconecta.presentacion;

import com.mediconecta.negocio.IntegracionExternaException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Traduce una falla de un sistema externo (SOAP legado, partner REST o IA) que
 * no tuvo fallback a {@code 502 Bad Gateway}, para distinguirla de un
 * {@code 400} por un pedido mal formado.
 */
@Provider
public class ManejadorIntegracionExterna implements ExceptionMapper<IntegracionExternaException> {

    @Context
    private UriInfo uri;

    @Override
    public Response toResponse(IntegracionExternaException excepcion) {
        return Problemas.respuesta(Response.Status.BAD_GATEWAY.getStatusCode(), "integracion-externa",
                excepcion.getMessage(), uri).build();
    }
}
