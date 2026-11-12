package com.mediconecta.presentacion;

import com.mediconecta.negocio.CredencialesInvalidasException;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/** Traduce credenciales incorrectas a {@code 401 Unauthorized}. */
@Provider
public class ManejadorCredencialesInvalidas implements ExceptionMapper<CredencialesInvalidasException> {

    @Override
    public Response toResponse(CredencialesInvalidasException excepcion) {
        int codigo = Response.Status.UNAUTHORIZED.getStatusCode();
        return Response.status(codigo)
                .entity(new ErrorRespuesta(excepcion.getMessage(), codigo))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }
}
