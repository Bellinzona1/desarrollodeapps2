package com.mediconecta.seguridad;

import com.mediconecta.negocio.Inyeccion;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.security.enterprise.AuthenticationStatus;
import jakarta.security.enterprise.authentication.mechanism.http.HttpMessageContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * El mecanismo JWT frente a tokens validos, invalidos y ausentes, en recursos
 * protegidos y publicos. Las interfaces de Servlet y Jakarta Security se
 * reemplazan por proxies.
 */
class MecanismoAutenticacionJwtTest {

    private ServicioJwt jwt;

    private MecanismoAutenticacionJwt mecanismo;

    private final StringWriter cuerpo = new StringWriter();

    private final Map<String, String> cabecerasRespuesta = new HashMap<>();

    private final int[] estado = {200};

    private final Object[] login = new Object[2];

    @BeforeEach
    void preparar() {
        jwt = new ServicioJwt("una-clave-de-prueba-de-al-menos-32-bytes!!".getBytes(StandardCharsets.UTF_8),
                Duration.ofMinutes(5), Clock.systemUTC());
        mecanismo = new MecanismoAutenticacionJwt();
        Inyeccion.campo(mecanismo, "servicioJwt", jwt);
    }

    private final Map<String, Object> atributos = new HashMap<>();

    private HttpServletRequest pedido(String autorizacion) {
        return (HttpServletRequest) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{HttpServletRequest.class}, (proxy, metodo, args) -> switch (metodo.getName()) {
                    case "getHeader" -> "Authorization".equals(args[0]) ? autorizacion : null;
                    case "setAttribute" -> atributos.put((String) args[0], args[1]);
                    case "getAttribute" -> atributos.get((String) args[0]);
                    case "getRequestURI" -> "/mediconecta/api/turnos";
                    default -> null;
                });
    }

    private HttpServletResponse respuesta() {
        PrintWriter escritor = new PrintWriter(cuerpo);
        return (HttpServletResponse) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{HttpServletResponse.class}, (proxy, metodo, args) -> {
                    switch (metodo.getName()) {
                        case "setStatus" -> estado[0] = (Integer) args[0];
                        case "setHeader", "setContentType" -> cabecerasRespuesta.put(
                                metodo.getName().equals("setHeader") ? (String) args[0] : "Content-Type",
                                (String) args[metodo.getName().equals("setHeader") ? 1 : 0]);
                        case "getWriter" -> {
                            return escritor;
                        }
                        case "flushBuffer" -> escritor.flush();
                        default -> {
                            return null;
                        }
                    }
                    return null;
                });
    }

    private HttpMessageContext contexto(boolean protegido, HttpServletRequest pedido) {
        return (HttpMessageContext) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{HttpMessageContext.class}, (proxy, metodo, args) -> switch (metodo.getName()) {
                    case "isProtected" -> protegido;
                    case "getRequest" -> pedido;
                    case "notifyContainerAboutLogin" -> {
                        login[0] = args[0];
                        login[1] = args[1];
                        yield AuthenticationStatus.SUCCESS;
                    }
                    case "doNothing" -> AuthenticationStatus.NOT_DONE;
                    case "responseUnauthorized" -> AuthenticationStatus.SEND_FAILURE;
                    default -> null;
                });
    }

    private AuthenticationStatus validar(String autorizacion, boolean protegido) {
        HttpServletRequest pedido = pedido(autorizacion);
        return mecanismo.validateRequest(pedido, respuesta(), contexto(protegido, pedido));
    }

    private JsonObject problema() {
        return Json.createReader(new StringReader(cuerpo.toString())).readObject();
    }

    @Test
    void unTokenValidoInformaUsuarioYRolesAlContenedor() {
        String token = jwt.emitir("recepcion", List.of("RECEPCION"));
        assertEquals(AuthenticationStatus.SUCCESS, validar("Bearer " + token, true));
        assertEquals("recepcion", login[0]);
        assertEquals(Set.of("RECEPCION"), login[1]);
        assertEquals(AuthenticationStatus.SUCCESS, validar("bearer " + token, true));
    }

    @Test
    void sinTokenEnUnRecursoProtegidoPideEl401AlContenedorYLaPaginaDeErrorEscribeElProblema() throws Exception {
        assertEquals(AuthenticationStatus.SEND_FAILURE, validar(null, true));
        assertTrue(cabecerasRespuesta.get("WWW-Authenticate").startsWith("Bearer"));
        assertEquals(MecanismoAutenticacionJwt.MOTIVO_SIN_TOKEN, atributos.get(MecanismoAutenticacionJwt.ATRIBUTO_MOTIVO));
        assertEquals("", cuerpo.toString(), "el mecanismo ya no escribe la respuesta");

        atributos.put(jakarta.servlet.RequestDispatcher.ERROR_REQUEST_URI, "/mediconecta/api/turnos");
        new RespuestaNoAutenticado().service(pedido(null), respuesta());
        assertEquals(401, estado[0]);
        assertTrue(cabecerasRespuesta.get("Content-Type").startsWith("application/problem+json"));
        JsonObject problema = problema();
        assertEquals(401, problema.getInt("status"));
        assertEquals(401, problema.getInt("codigo"));
        assertEquals("/mediconecta/api/turnos", problema.getString("instance"));
        assertEquals(problema.getString("detail"), problema.getString("error"));
    }

    @Test
    void unTokenAdulteradoEsRechazado() {
        String token = jwt.emitir("paciente", List.of("PACIENTE"));
        String[] partes = token.split("\\.");
        String contenido = new String(Base64.getUrlDecoder().decode(partes[1]), StandardCharsets.UTF_8)
                .replace("PACIENTE", "ADMIN");
        String adulterado = partes[0] + "." + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(contenido.getBytes(StandardCharsets.UTF_8)) + "." + partes[2];

        assertEquals(AuthenticationStatus.SEND_FAILURE, validar("Bearer " + adulterado, true));
        assertTrue(((String) atributos.get(MecanismoAutenticacionJwt.ATRIBUTO_MOTIVO)).contains("firma"));
        assertNull(login[0]);
    }

    @Test
    void unTokenConAlgNoneEsRechazado() {
        String cabecera = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"none\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String contenido = Base64.getUrlEncoder().withoutPadding().encodeToString(
                "{\"iss\":\"mediconecta\",\"sub\":\"admin\",\"groups\":[\"ADMIN\"],\"exp\":9999999999}"
                        .getBytes(StandardCharsets.UTF_8));
        assertEquals(AuthenticationStatus.SEND_FAILURE, validar("Bearer " + cabecera + "." + contenido + ".", true));
        assertNull(login[0]);
    }

    @Test
    void unTokenBasuraEnUnRecursoPublicoNoBloqueaElAcceso() {
        assertEquals(AuthenticationStatus.NOT_DONE, validar("Bearer basura", false));
        assertEquals(AuthenticationStatus.NOT_DONE, validar(null, false));
        assertEquals(200, estado[0]);
        assertTrue(atributos.isEmpty());
    }

    @Test
    void soloAceptaElEsquemaBearer() {
        assertNull(MecanismoAutenticacionJwt.extraerToken(pedido("Basic dXNlcjpwYXNz")));
        assertNull(MecanismoAutenticacionJwt.extraerToken(pedido("Bearer    ")));
        assertNull(MecanismoAutenticacionJwt.extraerToken(pedido(null)));
        assertEquals("abc", MecanismoAutenticacionJwt.extraerToken(pedido("Bearer abc ")));
    }

    @Test
    void elCuerpoDelProblemaEscapaComillasYCaracteresDeControl() {
        JsonObject problema = Json.createReader(new StringReader(
                MecanismoAutenticacionJwt.cuerpoProblema("mensaje con \"comillas\"\n<script>", null))).readObject();
        assertEquals("mensaje con \"comillas\"\n<script>", problema.getString("detail"));
        assertTrue(!problema.containsKey("instance"));
    }

    @Test
    void laPaginaDeErrorSinAtributosUsaElMotivoGenerico() throws Exception {
        new RespuestaNoAutenticado().service(pedido(null), respuesta());
        JsonObject problema = problema();
        assertEquals(MecanismoAutenticacionJwt.MOTIVO_SIN_TOKEN, problema.getString("detail"));
        assertTrue(!problema.containsKey("instance"));
        assertEquals("no-store", cabecerasRespuesta.get("Cache-Control"));
    }
}
