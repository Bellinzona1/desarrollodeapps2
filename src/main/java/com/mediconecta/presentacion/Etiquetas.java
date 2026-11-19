package com.mediconecta.presentacion;

import com.mediconecta.modelo.Paciente;
import com.mediconecta.modelo.Turno;

import jakarta.ws.rs.core.CacheControl;
import jakarta.ws.rs.core.EntityTag;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Request;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * {@code ETag} y pedidos condicionales (RFC 9110, seccion 13).
 *
 * <p>La etiqueta es un hash de los campos que el cliente ve del recurso: si
 * cualquiera cambia (estado del turno, datos de contacto del paciente), cambia
 * la etiqueta. Se calcula en la presentacion porque es un detalle de HTTP; las
 * entidades no tienen columna de version y no hizo falta agregarla.</p>
 *
 * <ul>
 *   <li>{@code GET} con {@code If-None-Match} igual a la etiqueta actual:
 *       {@code 304 Not Modified}, sin cuerpo.</li>
 *   <li>{@code PUT} con {@code If-Match} distinto de la etiqueta actual:
 *       {@code 412 Precondition Failed}: otro usuario cambio el recurso desde
 *       que el cliente lo leyo (control de concurrencia optimista).</li>
 *   <li>Sin esas cabeceras todo funciona igual que antes: son opcionales para
 *       no romper a los clientes existentes.</li>
 * </ul>
 */
public final class Etiquetas {

    /** Las respuestas con ETag se pueden guardar, pero hay que revalidarlas siempre. */
    static final CacheControl REVALIDAR = revalidar();

    private Etiquetas() {
    }

    private static CacheControl revalidar() {
        CacheControl politica = new CacheControl();
        politica.setPrivate(true);
        politica.setNoCache(true);
        politica.setNoTransform(false);
        return politica;
    }

    public static EntityTag de(Turno turno) {
        return etiqueta(turno.getId(), turno.getEstado(), turno.isArchivado(), turno.getFechaHora(),
                turno.getEspecialidad(), turno.getMotivo(),
                turno.getPaciente() == null ? null : turno.getPaciente().getId(),
                turno.getProfesional() == null ? null : turno.getProfesional().getId());
    }

    public static EntityTag de(Paciente paciente) {
        return etiqueta(paciente.getId(), paciente.getDocumento(), paciente.getNombre(), paciente.getEmail(),
                paciente.getTelefono(), paciente.getCobertura(), paciente.isActivo(), paciente.getUsuario());
    }

    /**
     * Evalua {@code If-Match}/{@code If-None-Match} contra la etiqueta actual.
     *
     * @return la respuesta a devolver (304 o 412) o {@code null} si el pedido
     *         puede seguir
     */
    static Response precondicionFallida(Request pedido, EntityTag actual, UriInfo uri) {
        Response.ResponseBuilder corte = pedido == null ? null : pedido.evaluatePreconditions(actual);
        if (corte == null) {
            return null;
        }
        Response provisoria = corte.build();
        if (provisoria.getStatus() == Response.Status.NOT_MODIFIED.getStatusCode()) {
            return Response.notModified(actual).cacheControl(REVALIDAR).build();
        }
        return Problemas.respuesta(Response.Status.PRECONDITION_FAILED.getStatusCode(), "precondicion-fallida",
                        "El recurso cambio desde la ultima vez que lo leyo. Vuelva a consultarlo e intente de nuevo.",
                        uri)
                .tag(actual)
                .build();
    }

    /** {@code true} si el pedido trae {@code If-Match} o {@code If-None-Match}. */
    static boolean tieneCondicion(HttpHeaders cabeceras) {
        return cabeceras != null
                && (cabeceras.getHeaderString(HttpHeaders.IF_MATCH) != null
                || cabeceras.getHeaderString(HttpHeaders.IF_NONE_MATCH) != null);
    }

    /** {@code 200} con el cuerpo, su etiqueta y la politica de cache. */
    static Response ok(Object cuerpo, EntityTag etiqueta) {
        return Response.ok(cuerpo).tag(etiqueta).cacheControl(REVALIDAR).build();
    }

    private static EntityTag etiqueta(Object... campos) {
        StringBuilder texto = new StringBuilder();
        for (Object campo : campos) {
            texto.append(Objects.toString(campo, "")).append('\u001f');
        }
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(texto.toString().getBytes(StandardCharsets.UTF_8));
            return new EntityTag(HexFormat.of().formatHex(hash, 0, 16));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("La JVM no soporta SHA-256", e);
        }
    }
}
