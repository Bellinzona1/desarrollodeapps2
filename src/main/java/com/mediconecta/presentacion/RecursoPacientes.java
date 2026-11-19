package com.mediconecta.presentacion;

import com.mediconecta.modelo.Paciente;
import com.mediconecta.modelo.Turno;
import com.mediconecta.negocio.GestionPacientes;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.EntityTag;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Request;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

import java.net.URI;
import java.util.List;

/** API REST del componente Gestion de Pacientes. */
@Path("/pacientes")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class RecursoPacientes {

    @Inject
    private GestionPacientes gestionPacientes;

    @Context
    private UriInfo uriInfo;

    @Context
    private Request pedidoHttp;

    @Context
    private HttpHeaders cabeceras;

    @GET
    public List<Paciente> listar(@QueryParam("q") String texto) {
        return gestionPacientes.buscar(texto);
    }

    /** Ficha del paciente asociado al usuario que tiene la sesion abierta. */
    @GET
    @Path("/mi-ficha")
    public Paciente miFicha() {
        return gestionPacientes.miFicha();
    }

    /** Ficha con {@code ETag}; {@code 304} si el cliente ya tiene la version vigente. */
    @GET
    @Path("/{documento}")
    public Response obtener(@PathParam("documento") String documento) {
        Paciente paciente = gestionPacientes.buscarPorDocumento(documento);
        EntityTag etiqueta = Etiquetas.de(paciente);
        Response corte = Etiquetas.precondicionFallida(pedidoHttp, etiqueta, uriInfo);
        return corte != null ? corte : Etiquetas.ok(paciente, etiqueta);
    }

    @POST
    public Response registrar(Paciente paciente) {
        Paciente creado = gestionPacientes.registrarPaciente(paciente);

        // Plantilla + build(valor): UriBuilder codifica el documento en lugar de
        // interpretarlo como parte de la ruta (con path(texto) un "{" rompia el URI).
        URI ubicacion = uriInfo.getAbsolutePathBuilder()
                .path("{documento}")
                .build(creado.getDocumento());

        return Response.created(ubicacion).entity(creado).build();
    }

    /**
     * Actualiza los datos de contacto. Con {@code If-Match} evita pisar los
     * cambios que otro usuario hizo despues de que este leyo la ficha (412).
     */
    @PUT
    @Path("/{documento}")
    public Response actualizar(@PathParam("documento") String documento, Paciente datos) {
        if (Etiquetas.tieneCondicion(cabeceras)) {
            Response corte = Etiquetas.precondicionFallida(pedidoHttp,
                    Etiquetas.de(gestionPacientes.buscarPorDocumento(documento)), uriInfo);
            if (corte != null) {
                return corte;
            }
        }
        Paciente actualizado = gestionPacientes.actualizarDatos(documento, datos);
        return Etiquetas.ok(actualizado, Etiquetas.de(actualizado));
    }

    @GET
    @Path("/{documento}/turnos")
    public List<Turno> historial(@PathParam("documento") String documento) {
        return gestionPacientes.historialDeTurnos(documento);
    }
}
