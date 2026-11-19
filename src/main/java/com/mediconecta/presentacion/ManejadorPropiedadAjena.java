package com.mediconecta.presentacion;

import com.mediconecta.negocio.AccesoDenegadoException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Traduce a {@code 403 Forbidden} el intento de operar sobre un recurso ajeno
 * (BOLA / IDOR): el rol tiene permiso para la operacion, pero no sobre ese
 * turno, esa ficha o esa agenda. Lo detecta el negocio con
 * {@code SessionContext.getCallerPrincipal()}.
 */
@Provider
public class ManejadorPropiedadAjena implements ExceptionMapper<AccesoDenegadoException> {

    @Context
    private UriInfo uri;

    @Override
    public Response toResponse(AccesoDenegadoException excepcion) {
        return Problemas.respuesta(Response.Status.FORBIDDEN.getStatusCode(), "recurso-ajeno",
                excepcion.getMessage(), uri).build();
    }
}
