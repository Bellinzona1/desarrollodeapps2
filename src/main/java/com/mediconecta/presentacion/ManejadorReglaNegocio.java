package com.mediconecta.presentacion;

import com.mediconecta.negocio.ReglaNegocioException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Traduce una regla de negocio incumplida a {@code 400 Bad Request}.
 *
 * <p>Es el mecanismo que mantiene al negocio libre de cualquier referencia a
 * HTTP y evita repetir {@code try/catch} en cada metodo de cada recurso.</p>
 */
@Provider
public class ManejadorReglaNegocio implements ExceptionMapper<ReglaNegocioException> {

    @Context
    private UriInfo uri;

    @Override
    public Response toResponse(ReglaNegocioException excepcion) {
        return Problemas.respuesta(Response.Status.BAD_REQUEST.getStatusCode(), "regla-de-negocio",
                excepcion.getMessage(), uri).build();
    }
}
