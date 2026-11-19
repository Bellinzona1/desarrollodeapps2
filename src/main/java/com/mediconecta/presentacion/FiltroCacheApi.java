package com.mediconecta.presentacion;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.ext.Provider;

/**
 * Las respuestas de la API traen datos personales y de salud: por defecto no
 * se guardan en ninguna cache ({@code Cache-Control: no-store}).
 *
 * <p>Las que ya definieron su politica la conservan: las que llevan
 * {@code ETag} usan {@code private, no-cache} (ver {@link Etiquetas}), que
 * permite el pedido condicional con {@code If-None-Match}.</p>
 */
@Provider
public class FiltroCacheApi implements ContainerResponseFilter {

    @Override
    public void filter(ContainerRequestContext pedido, ContainerResponseContext respuesta) {
        if (!respuesta.getHeaders().containsKey(HttpHeaders.CACHE_CONTROL)) {
            respuesta.getHeaders().putSingle(HttpHeaders.CACHE_CONTROL, "no-store");
        }
    }
}
