package com.mediconecta.presentacion;

import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Red de contencion: todo error que no tiene un mapper especifico.
 *
 * <ul>
 *   <li>Errores del propio JAX-RS ({@code 404} de una ruta inexistente,
 *       {@code 405}, {@code 406}, {@code 415}): se respeta su codigo y se
 *       devuelve en formato Problem Details.</li>
 *   <li>Cuerpo que no es JSON valido: {@code 400}.</li>
 *   <li>Cualquier otra excepcion: {@code 500} con un mensaje generico y un
 *       identificador de incidente. El detalle (y el stack trace) va solo al
 *       log del servidor: mostrarlo al cliente le daria informacion a un
 *       atacante (lo reporta OWASP ZAP como "Application Error Disclosure").</li>
 * </ul>
 *
 * <p>JAX-RS elige el mapper del tipo mas cercano a la excepcion, asi que los
 * mappers especificos (reglas de negocio, 403, 404 de negocio...) siguen
 * ganando sobre este.</p>
 */
@Provider
public class ManejadorErrorInesperado implements ExceptionMapper<Throwable> {

    private static final Logger LOG = Logger.getLogger(ManejadorErrorInesperado.class.getName());

    @Context
    private UriInfo uri;

    @Override
    public Response toResponse(Throwable excepcion) {
        if (excepcion instanceof WebApplicationException web) {
            return desdeJaxRs(web);
        }
        if (excepcion instanceof ProcessingException) {
            return Problemas.respuesta(Response.Status.BAD_REQUEST.getStatusCode(), "cuerpo-invalido",
                    "El cuerpo del pedido no es un JSON valido para esta operacion.", uri).build();
        }

        String incidente = UUID.randomUUID().toString();
        LOG.log(Level.SEVERE, "[ERROR] incidente " + incidente, excepcion);
        return Problemas.respuesta(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), "error-interno",
                "Ocurrio un error inesperado. Informe este codigo de incidente: " + incidente + ".", uri).build();
    }

    private Response desdeJaxRs(WebApplicationException web) {
        Response original = web.getResponse();
        int estado = original.getStatus();
        String detalle = switch (estado) {
            case 400 -> "El pedido esta mal formado.";
            case 404 -> "El recurso solicitado no existe.";
            case 405 -> "El metodo HTTP no esta permitido para este recurso.";
            case 406 -> "No se puede responder en el formato pedido (Accept).";
            case 415 -> "El tipo de contenido del pedido no esta soportado. Use application/json.";
            default -> estado >= 500 ? "Ocurrio un error inesperado." : "El pedido no se pudo procesar.";
        };
        if (estado >= 500) {
            LOG.log(Level.SEVERE, "[ERROR] error de JAX-RS", web);
        }
        Response.ResponseBuilder respuesta = Problemas.respuesta(estado, "http-" + estado, detalle, uri);
        String permitidos = original.getHeaderString(HttpHeaders.ALLOW);
        if (permitidos != null) {
            respuesta.header(HttpHeaders.ALLOW, permitidos);
        }
        return respuesta.build();
    }
}
