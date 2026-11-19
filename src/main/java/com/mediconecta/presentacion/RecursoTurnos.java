package com.mediconecta.presentacion;

import com.mediconecta.modelo.Turno;
import com.mediconecta.negocio.FachadaReservas;
import com.mediconecta.negocio.GestionTurnos;
import com.mediconecta.negocio.ResultadoReserva;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.EntityTag;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Request;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

import java.net.URI;
import java.util.List;
import java.util.function.LongFunction;

/**
 * API REST de turnos.
 *
 * <p>Esta clase no valida nada ni decide nada: traduce HTTP a llamadas de
 * negocio y de vuelta. El alta va por {@link FachadaReservas} porque reservar
 * involucra a tres componentes, y coordinarlos no es tarea de la presentacion.</p>
 *
 * <p>Detalles de HTTP que si resuelve esta capa:</p>
 * <ul>
 *   <li>{@code POST} acepta {@code Idempotency-Key}: un reintento con la misma
 *       clave devuelve el turno ya creado con la cabecera
 *       {@code Idempotency-Replayed: true};</li>
 *   <li>{@code GET /{id}} devuelve {@code ETag} y responde {@code 304} a un
 *       {@code If-None-Match} vigente;</li>
 *   <li>las transiciones ({@code cancelar}, {@code atender}, {@code archivar})
 *       aceptan {@code If-Match} y responden {@code 412} si el turno cambio.</li>
 * </ul>
 */
@Path("/turnos")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class RecursoTurnos {

    static final String CABECERA_IDEMPOTENCIA = "Idempotency-Key";

    static final String CABECERA_REPETIDA = "Idempotency-Replayed";

    @Inject
    private GestionTurnos gestionTurnos;

    @Inject
    private FachadaReservas fachadaReservas;

    @Context
    private UriInfo uriInfo;

    @Context
    private Request pedidoHttp;

    @Context
    private HttpHeaders cabeceras;

    @GET
    public List<Turno> listar() {
        return gestionTurnos.listarTurnos();
    }

    @GET
    @Path("/{id}")
    public Response obtener(@PathParam("id") Long id) {
        Turno turno = gestionTurnos.obtenerTurno(id);
        EntityTag etiqueta = Etiquetas.de(turno);
        Response corte = Etiquetas.precondicionFallida(pedidoHttp, etiqueta, uriInfo);
        return corte != null ? corte : Etiquetas.ok(turno, etiqueta);
    }

    @POST
    public Response crear(@HeaderParam(CABECERA_IDEMPOTENCIA) String claveIdempotencia, PedidoTurno pedido) {
        ResultadoReserva resultado = fachadaReservas.reservarIdempotente(
                pedido == null ? null : pedido.aSolicitud(), claveIdempotencia);
        Turno turno = resultado.turno();

        URI ubicacion = uriInfo.getAbsolutePathBuilder()
                .path(String.valueOf(turno.getId()))
                .build();

        Response.ResponseBuilder respuesta = Response.created(ubicacion)
                .entity(turno)
                .tag(Etiquetas.de(turno));
        if (resultado.repetida()) {
            respuesta.header(CABECERA_REPETIDA, "true");
        }
        return respuesta.build();
    }

    @PUT
    @Path("/{id}/cancelar")
    public Response cancelar(@PathParam("id") Long id) {
        return transicion(id, gestionTurnos::cancelarTurno);
    }

    /** Registra la atencion: PROGRAMADO -> ATENDIDO (409 desde otro estado). */
    @PUT
    @Path("/{id}/atender")
    public Response atender(@PathParam("id") Long id) {
        return transicion(id, gestionTurnos::atenderTurno);
    }

    /** Saca un turno cancelado de la agenda operativa sin borrarlo. */
    @PUT
    @Path("/{id}/archivar")
    public Response archivar(@PathParam("id") Long id) {
        return transicion(id, gestionTurnos::archivarTurno);
    }

    /**
     * Si el pedido trae {@code If-Match}, primero compara con la etiqueta
     * actual; despues delega la transicion en el negocio (patron State).
     */
    private Response transicion(Long id, LongFunction<Turno> operacion) {
        if (Etiquetas.tieneCondicion(cabeceras)) {
            Response corte = Etiquetas.precondicionFallida(pedidoHttp, Etiquetas.de(gestionTurnos.obtenerTurno(id)), uriInfo);
            if (corte != null) {
                return corte;
            }
        }
        Turno turno = operacion.apply(id);
        return Etiquetas.ok(turno, Etiquetas.de(turno));
    }
}
