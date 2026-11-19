package com.mediconecta.seguridad;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * Pagina de error del 401: escribe el Problem Details de "no autenticado".
 *
 * <p>Esta declarada en {@code web.xml} como {@code <error-page>} del codigo 401.
 * El contenedor la invoca despues de que {@link MecanismoAutenticacionJwt}
 * rechaza un pedido sin token o con un token invalido. El motivo llega en el
 * atributo {@link MecanismoAutenticacionJwt#ATRIBUTO_MOTIVO} y la ruta original
 * en el atributo estandar {@code jakarta.servlet.error.request_uri}.</p>
 *
 * <p>No tiene datos ni logica de negocio: es publica a proposito (no esta bajo
 * {@code /api/*}).</p>
 */
@WebServlet(name = "RespuestaNoAutenticado", urlPatterns = RespuestaNoAutenticado.RUTA)
public class RespuestaNoAutenticado extends HttpServlet {

    private static final long serialVersionUID = 1L;

    static final String RUTA = "/errores/no-autenticado";

    @Override
    protected void service(HttpServletRequest pedido, HttpServletResponse respuesta) throws IOException {
        Object motivo = pedido.getAttribute(MecanismoAutenticacionJwt.ATRIBUTO_MOTIVO);
        Object ruta = pedido.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);

        respuesta.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        respuesta.setHeader("WWW-Authenticate", MecanismoAutenticacionJwt.DESAFIO);
        respuesta.setHeader("Cache-Control", "no-store");
        respuesta.setContentType("application/problem+json;charset=UTF-8");
        respuesta.getWriter().write(MecanismoAutenticacionJwt.cuerpoProblema(
                motivo instanceof String texto ? texto : MecanismoAutenticacionJwt.MOTIVO_SIN_TOKEN,
                ruta instanceof String uri ? uri : null));
    }
}
