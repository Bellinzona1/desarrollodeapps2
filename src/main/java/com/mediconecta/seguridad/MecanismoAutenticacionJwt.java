package com.mediconecta.seguridad;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonObjectBuilder;
import jakarta.security.enterprise.AuthenticationStatus;
import jakarta.security.enterprise.authentication.mechanism.http.HttpAuthenticationMechanism;
import jakarta.security.enterprise.authentication.mechanism.http.HttpMessageContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.HashSet;

/**
 * Autenticacion por <b>JWT</b> integrada al contenedor (Jakarta Security 3.0).
 *
 * <p>Reemplaza al login FORM de la Entrega 1. El contenedor invoca este
 * mecanismo en <b>cada</b> peticion, antes de llegar al recurso:</p>
 *
 * <ol>
 *   <li>Si llega {@code Authorization: Bearer <token>} y el token es valido, le
 *       informa al contenedor quien es el usuario y que roles tiene
 *       ({@code notifyContainerAboutLogin}). A partir de ahi todo sigue igual
 *       que antes: {@code @RolesAllowed} en los EJB, {@code isCallerInRole},
 *       {@code getCallerPrincipal} y el {@code SecurityContext} de JAX-RS
 *       funcionan con esa identidad.</li>
 *   <li>Si el recurso esta protegido en {@code web.xml} y no hay token (o es
 *       invalido), corta la peticion con un <b>401</b> en formato Problem
 *       Details, igual que el resto de la API (lo escribe
 *       {@link RespuestaNoAutenticado}).</li>
 *   <li>Si el recurso es publico ({@code /api/salud}, {@code /api/auth/login},
 *       las pantallas estaticas) y no hay token, deja pasar sin identidad.</li>
 * </ol>
 *
 * <p>Es stateless: el servidor no guarda nada de la autenticacion entre una
 * peticion y la siguiente; cada peticion trae su token. (La sesion HTTP sigue
 * existiendo solo para la conversacion del asistente {@code @Stateful}.)</p>
 *
 * <p>Requisito en WildFly: el {@code application-security-domain} "other" de
 * Undertow debe tener {@code integrated-jaspi=false}, para que acepte la
 * identidad que arma este mecanismo en lugar de buscar al usuario en los
 * archivos de properties. Lo configura {@code scripts/wildfly/configurar-seguridad.cli}.</p>
 */
@ApplicationScoped
public class MecanismoAutenticacionJwt implements HttpAuthenticationMechanism {

    private static final String PREFIJO = "Bearer ";

    /** Atributo del pedido con el motivo del 401, para {@link RespuestaNoAutenticado}. */
    static final String ATRIBUTO_MOTIVO = "mediconecta.autenticacion.motivo";

    static final String MOTIVO_SIN_TOKEN = "Debe iniciar sesion para usar esta operacion.";

    static final String DESAFIO = "Bearer realm=\"mediconecta\"";

    @Inject
    private ServicioJwt servicioJwt;

    @Override
    public AuthenticationStatus validateRequest(HttpServletRequest peticion,
                                                HttpServletResponse respuesta,
                                                HttpMessageContext contexto) {

        String token = extraerToken(peticion);

        if (token != null) {
            try {
                ClaimsJwt claims = servicioJwt.validar(token);
                return contexto.notifyContainerAboutLogin(claims.usuario(), new HashSet<>(claims.roles()));
            } catch (TokenInvalidoException e) {
                if (contexto.isProtected()) {
                    return rechazar(peticion, respuesta, e.getMessage(), contexto);
                }
                return contexto.doNothing();
            }
        }

        if (contexto.isProtected()) {
            return rechazar(peticion, respuesta, MOTIVO_SIN_TOKEN, contexto);
        }
        return contexto.doNothing();
    }

    static String extraerToken(HttpServletRequest peticion) {
        String cabecera = peticion.getHeader("Authorization");
        if (cabecera == null || !cabecera.regionMatches(true, 0, PREFIJO, 0, PREFIJO.length())) {
            return null;
        }
        String token = cabecera.substring(PREFIJO.length()).trim();
        return token.isEmpty() ? null : token;
    }

    /**
     * Rechaza con 401 delegando el cuerpo en la pagina de error del contenedor.
     *
     * <p>Antes el mecanismo escribia el JSON y hacia {@code flushBuffer()}; despues
     * Undertow intentaba su propio {@code sendError(401)} sobre una respuesta ya
     * confirmada y registraba un ERROR con el stack trace completo (unos 6 KB) por
     * cada pedido sin token: un atacante sin credenciales podia llenar el disco con
     * el log (se observo en el escaneo de OWASP ZAP). Ahora se pide el 401 al
     * contenedor ({@code responseUnauthorized()}, es decir {@code sendError}) y el
     * Problem Details lo escribe {@link RespuestaNoAutenticado}, declarada como
     * {@code <error-page>} del 401 en {@code web.xml}. El motivo concreto (token
     * vencido, firma invalida...) viaja como atributo del pedido.</p>
     */
    private AuthenticationStatus rechazar(HttpServletRequest peticion, HttpServletResponse respuesta,
                                          String mensaje, HttpMessageContext contexto) {
        peticion.setAttribute(ATRIBUTO_MOTIVO, mensaje);
        respuesta.setHeader("WWW-Authenticate", DESAFIO);
        return contexto.responseUnauthorized();
    }

    /**
     * Problem Details (RFC 9457) del 401, armado con JSON-P para que el mensaje
     * y la ruta queden bien escapados. Lleva tambien {@code error} y
     * {@code codigo}, igual que el resto de los errores de la API.
     */
    static String cuerpoProblema(String mensaje, String ruta) {
        JsonObjectBuilder cuerpo = Json.createObjectBuilder()
                .add("type", "urn:mediconecta:problema:no-autenticado")
                .add("title", "Unauthorized")
                .add("status", HttpServletResponse.SC_UNAUTHORIZED)
                .add("detail", mensaje)
                .add("error", mensaje)
                .add("codigo", HttpServletResponse.SC_UNAUTHORIZED);
        if (ruta != null) {
            cuerpo.add("instance", ruta);
        }
        return cuerpo.build().toString();
    }
}
