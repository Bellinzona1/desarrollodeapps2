package com.mediconecta.presentacion;

import com.mediconecta.negocio.NoEncontradoException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/** Traduce "la entidad no existe" a {@code 404 Not Found}. */
@Provider
public class ManejadorNoEncontrado implements ExceptionMapper<NoEncontradoException> {

    @Context
    private UriInfo uri;

    @Override
    public Response toResponse(NoEncontradoException excepcion) {
        return Problemas.respuesta(Response.Status.NOT_FOUND.getStatusCode(), "no-encontrado",
                excepcion.getMessage(), uri).build();
    }
}
