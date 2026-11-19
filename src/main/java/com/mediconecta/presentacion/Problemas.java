package com.mediconecta.presentacion;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

/**
 * Arma las respuestas de error en formato Problem Details (RFC 9457).
 *
 * <p>Todos los {@code ExceptionMapper} pasan por aca: asi el formato, el tipo
 * de contenido y los identificadores {@code type} son los mismos en toda la API.
 * Los {@code type} son URN propios ({@code urn:mediconecta:problema:...}): la RFC
 * pide un URI que identifique el tipo de problema, no que sea navegable.</p>
 */
public final class Problemas {

    /** Tipo de contenido de la RFC 9457. */
    public static final String TIPO_CONTENIDO = "application/problem+json";

    public static final MediaType MEDIA_TYPE = MediaType.valueOf(TIPO_CONTENIDO);

    private static final String PREFIJO_TIPO = "urn:mediconecta:problema:";

    private Problemas() {
    }

    /** {@code type} completo a partir de un nombre corto ("regla-de-negocio"). */
    public static String tipo(String nombre) {
        return PREFIJO_TIPO + nombre;
    }

    public static DetalleProblema cuerpo(int estado, String nombreTipo, String detalle, UriInfo uri) {
        Response.Status conocido = Response.Status.fromStatusCode(estado);
        String titulo = conocido != null ? conocido.getReasonPhrase() : tituloDe(estado);
        return new DetalleProblema(tipo(nombreTipo), titulo, estado, detalle, instancia(uri));
    }

    public static Response.ResponseBuilder respuesta(int estado, String nombreTipo, String detalle, UriInfo uri) {
        return Response.status(estado)
                .type(MEDIA_TYPE)
                .entity(cuerpo(estado, nombreTipo, detalle, uri));
    }

    /** Titulos de los codigos que {@link Response.Status} no trae (422, 429 en algunas versiones). */
    static String tituloDe(int estado) {
        return switch (estado) {
            case 422 -> "Unprocessable Content";
            case 428 -> "Precondition Required";
            case 429 -> "Too Many Requests";
            default -> "Error";
        };
    }

    private static String instancia(UriInfo uri) {
        try {
            return uri == null ? null : uri.getRequestUri().getRawPath();
        } catch (RuntimeException fueraDeUnPedido) {
            return null;
        }
    }
}
