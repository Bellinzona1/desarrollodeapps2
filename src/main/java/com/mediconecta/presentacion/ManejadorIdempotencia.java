package com.mediconecta.presentacion;

import com.mediconecta.negocio.IdempotenciaException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Errores de {@code Idempotency-Key}, con los codigos del draft IETF
 * "The Idempotency-Key HTTP Header Field":
 * <ul>
 *   <li>{@code 422} si la clave ya se uso con otro cuerpo;</li>
 *   <li>{@code 409} si hay otro pedido con la misma clave en curso.</li>
 * </ul>
 */
@Provider
public class ManejadorIdempotencia implements ExceptionMapper<IdempotenciaException> {

    @Context
    private UriInfo uri;

    @Override
    public Response toResponse(IdempotenciaException excepcion) {
        if (excepcion.getMotivo() == IdempotenciaException.Motivo.REUTILIZADA) {
            return Problemas.respuesta(422, "idempotency-key-reutilizada", excepcion.getMessage(), uri).build();
        }
        return Problemas.respuesta(Response.Status.CONFLICT.getStatusCode(), "idempotency-key-en-curso",
                excepcion.getMessage(), uri).build();
    }
}
