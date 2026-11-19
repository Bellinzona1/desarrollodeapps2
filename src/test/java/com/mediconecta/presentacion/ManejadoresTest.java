package com.mediconecta.presentacion;

import com.mediconecta.negocio.CredencialesInvalidasException;
import com.mediconecta.negocio.DemasiadosIntentosException;
import com.mediconecta.negocio.IdempotenciaException;
import com.mediconecta.negocio.IntegracionExternaException;

import jakarta.ejb.EJBAccessException;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.NotAllowedException;
import jakarta.ws.rs.NotSupportedException;
import jakarta.ws.rs.ServerErrorException;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Cada excepcion se traduce al codigo HTTP y al Problem Details que corresponde. */
class ManejadoresTest {

    private static DetalleProblema problema(Response respuesta) {
        assertEquals(Problemas.MEDIA_TYPE, respuesta.getMediaType());
        return (DetalleProblema) respuesta.getEntity();
    }

    @Test
    void demasiadosIntentosDa429ConRetryAfter() {
        Response respuesta = new ManejadorDemasiadosIntentos().toResponse(new DemasiadosIntentosException("espere", 90));
        assertEquals(429, respuesta.getStatus());
        assertEquals("90", respuesta.getHeaderString("Retry-After"));
        assertEquals("Too Many Requests", problema(respuesta).getTitle());
    }

    @Test
    void idempotenciaEnCursoDa409YReutilizadaDa422() {
        Response enCurso = new ManejadorIdempotencia().toResponse(
                new IdempotenciaException(IdempotenciaException.Motivo.EN_CURSO, "x"));
        assertEquals(409, enCurso.getStatus());
        Response reutilizada = new ManejadorIdempotencia().toResponse(
                new IdempotenciaException(IdempotenciaException.Motivo.REUTILIZADA, "x"));
        assertEquals(422, reutilizada.getStatus());
        assertEquals("Unprocessable Content", problema(reutilizada).getTitle());
    }

    @Test
    void credencialesInvalidasDa401ConDesafioBearer() {
        Response respuesta = new ManejadorCredencialesInvalidas().toResponse(new CredencialesInvalidasException());
        assertEquals(401, respuesta.getStatus());
        assertTrue(respuesta.getHeaderString("WWW-Authenticate").startsWith("Bearer"));
    }

    @Test
    void faltaDeRolDa403YFallaExternaDa502() {
        assertEquals(403, new ManejadorAccesoDenegado().toResponse(new EJBAccessException("x")).getStatus());
        Response externa = new ManejadorIntegracionExterna().toResponse(new IntegracionExternaException("caido"));
        assertEquals(502, externa.getStatus());
        assertEquals("caido", problema(externa).getDetail());
    }

    @Test
    void unErrorInesperadoDa500SinMostrarElDetalleInterno() {
        Response respuesta = new ManejadorErrorInesperado().toResponse(
                new NullPointerException("campo secreto del servidor"));
        assertEquals(500, respuesta.getStatus());
        String detalle = problema(respuesta).getDetail();
        assertFalse(detalle.contains("secreto"));
        assertTrue(detalle.contains("incidente"));
    }

    @Test
    void erroresDeJaxRsConservanSuCodigoYLaCabeceraAllow() {
        Response metodo = new ManejadorErrorInesperado().toResponse(
                new NotAllowedException("GET", new String[]{"POST"}));
        assertEquals(405, metodo.getStatus());
        assertTrue(metodo.getHeaderString(HttpHeaders.ALLOW).contains("GET"));
        assertEquals(415, new ManejadorErrorInesperado().toResponse(new NotSupportedException()).getStatus());
        assertEquals(503, new ManejadorErrorInesperado().toResponse(new ServerErrorException(503)).getStatus());
    }

    @Test
    void titulosDeCodigosPocoComunes() {
        assertEquals("Unprocessable Content", Problemas.tituloDe(422));
        assertEquals("Precondition Required", Problemas.tituloDe(428));
        assertEquals("Too Many Requests", Problemas.tituloDe(429));
        assertEquals("Error", Problemas.tituloDe(299));
        DetalleProblema sinPedido = Problemas.cuerpo(400, "x", "detalle", null);
        assertNull(sinPedido.getInstance());
        assertEquals("urn:mediconecta:problema:x", sinPedido.getType());
    }

    @Test
    void elFiltroAgregaLasCabecerasDeSeguridad() {
        Map<String, String> cabeceras = new HashMap<>();
        HttpServletResponse respuesta = (HttpServletResponse) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{HttpServletResponse.class}, (proxy, metodo, args) -> {
                    if (metodo.getName().equals("setHeader")) {
                        cabeceras.put((String) args[0], (String) args[1]);
                    }
                    return null;
                });
        FiltroCabecerasSeguridad.aplicar(respuesta);
        assertEquals("nosniff", cabeceras.get("X-Content-Type-Options"));
        assertEquals("DENY", cabeceras.get("X-Frame-Options"));
        assertTrue(cabeceras.get("Content-Security-Policy").contains("frame-ancestors 'none'"));
        assertTrue(cabeceras.get("Content-Security-Policy").contains("script-src 'self'"));
        assertFalse(cabeceras.get("Content-Security-Policy").contains("unsafe-inline"));
        assertEquals("no-referrer", cabeceras.get("Referrer-Policy"));
    }
}
