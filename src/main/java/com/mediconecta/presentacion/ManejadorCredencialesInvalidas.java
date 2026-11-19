package com.mediconecta.presentacion;

import com.mediconecta.negocio.CredencialesInvalidasException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/** Traduce credenciales incorrectas a {@code 401 Unauthorized}. */
@Provider
public class ManejadorCredencialesInvalidas implements ExceptionMapper<CredencialesInvalidasException> {

    @Context
    private UriInfo uri;

    @Override
    public Response toResponse(CredencialesInvalidasException excepcion) {
        return Problemas.respuesta(Response.Status.UNAUTHORIZED.getStatusCode(), "credenciales-invalidas",
                excepcion.getMessage(), uri)
                .header("WWW-Authenticate", "Bearer realm=\"mediconecta\"")
                .build();
    }
}
