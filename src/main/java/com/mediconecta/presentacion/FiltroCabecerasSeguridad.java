package com.mediconecta.presentacion;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * Cabeceras de seguridad para todas las respuestas (pantallas y API).
 *
 * <p>Son las que reporta OWASP ZAP cuando faltan:</p>
 * <ul>
 *   <li>{@code Content-Security-Policy}: solo scripts, estilos e imagenes del
 *       propio origen; nadie puede embeber la aplicacion en un iframe. El
 *       frontend no usa scripts ni estilos en linea (todo es modulo ES en
 *       {@code js/} y clases de {@code css/styles.css}): no hace falta
 *       {@code 'unsafe-inline'}, que ZAP reporta como alerta Medium. Cambiar
 *       un estilo desde JS con {@code elemento.style.x} sigue permitido. La unica
 *       excepcion externa es la fuente Inter de Google Fonts (hoja de estilos en
 *       {@code fonts.googleapis.com}, archivos en {@code fonts.gstatic.com}).</li>
 *   <li>{@code X-Content-Type-Options: nosniff}: el navegador no "adivina" el
 *       tipo de un archivo.</li>
 *   <li>{@code X-Frame-Options: DENY}: clickjacking, para navegadores sin CSP 2.</li>
 *   <li>{@code Referrer-Policy} y {@code Permissions-Policy}: no se filtra la
 *       URL a terceros y se apagan APIs del navegador que la app no usa.</li>
 * </ul>
 */
@WebFilter(filterName = "FiltroCabecerasSeguridad", urlPatterns = "/*")
public class FiltroCabecerasSeguridad implements Filter {

    static final String POLITICA_CONTENIDO = "default-src 'self'; script-src 'self'; style-src 'self' https://fonts.googleapis.com; "
            + "img-src 'self' data:; font-src 'self' https://fonts.gstatic.com; connect-src 'self'; object-src 'none'; "
            + "base-uri 'self'; form-action 'self'; frame-ancestors 'none'";

    @Override
    public void doFilter(ServletRequest pedido, ServletResponse respuesta, FilterChain cadena)
            throws IOException, ServletException {
        if (respuesta instanceof HttpServletResponse http) {
            aplicar(http);
        }
        cadena.doFilter(pedido, respuesta);
    }

    static void aplicar(HttpServletResponse http) {
        http.setHeader("Content-Security-Policy", POLITICA_CONTENIDO);
        http.setHeader("X-Content-Type-Options", "nosniff");
        http.setHeader("X-Frame-Options", "DENY");
        http.setHeader("Referrer-Policy", "no-referrer");
        http.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=(), payment=()");
        http.setHeader("Cross-Origin-Opener-Policy", "same-origin");
        http.setHeader("Cross-Origin-Resource-Policy", "same-origin");
    }
}
