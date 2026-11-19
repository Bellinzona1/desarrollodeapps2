package com.mediconecta.presentacion;

import com.mediconecta.mensajeria.DiagnosticoMensajeria;
import com.mediconecta.mensajeria.EstadoMensajeria;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Map;

/**
 * Diagnostico de la mensajeria (rol ADMIN, lo exige {@link DiagnosticoMensajeria}).
 *
 * <pre>
 * GET  /api/mensajeria/estado      -&gt; procesados por consumidor y ultimos mensajes en la DLQ
 * POST /api/mensajeria/prueba-dlq  -&gt; 202 {"clave":"prueba-dlq-..."}: manda un mensaje veneno
 * </pre>
 */
@Path("/mensajeria")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class RecursoMensajeria {

    @Inject
    private DiagnosticoMensajeria diagnostico;

    @GET
    @Path("/estado")
    public EstadoMensajeria estado() {
        return diagnostico.estado();
    }

    @POST
    @Path("/prueba-dlq")
    public Response pruebaDlq() {
        return Response.accepted(Map.of("clave", diagnostico.enviarMensajeVeneno())).build();
    }
}
