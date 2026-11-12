package com.mediconecta.presentacion;

import com.mediconecta.modelo.estado.TransicionInvalidaException;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Traduce una transicion de estado invalida del turno (patron State) a
 * {@code 409 Conflict}.
 *
 * <p>Se distingue del 400 de {@link ManejadorReglaNegocio}: un 400 dice "el
 * pedido esta mal" (fecha en el pasado, especialidad incoherente); un 409 dice
 * "el pedido es correcto, pero choca con el estado actual del recurso"
 * (cancelar un turno ya cancelado, atender uno cancelado).</p>
 */
@Provider
public class ManejadorTransicionInvalida implements ExceptionMapper<TransicionInvalidaException> {

    @Override
    public Response toResponse(TransicionInvalidaException excepcion) {
        int codigo = Response.Status.CONFLICT.getStatusCode();
        return Response.status(codigo)
                .entity(new ErrorRespuesta(excepcion.getMessage(), codigo))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }
}
