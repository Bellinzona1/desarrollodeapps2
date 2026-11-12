package com.mediconecta.seguridad;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.security.enterprise.AuthenticationStatus;
import jakarta.security.enterprise.authentication.mechanism.http.HttpAuthenticationMechanism;
import jakarta.security.enterprise.authentication.mechanism.http.HttpMessageContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
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
 *       invalido), corta la peticion con un <b>401</b> en el mismo formato JSON
 *       que el resto de la API.</li>
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
                    return rechazar(respuesta, e.getMessage(), contexto);
                }
                return contexto.doNothing();
            }
        }

        if (contexto.isProtected()) {
            return rechazar(respuesta, "Debe iniciar sesion para usar esta operacion.", contexto);
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

    private AuthenticationStatus rechazar(HttpServletResponse respuesta, String mensaje,
                                          HttpMessageContext contexto) {
        try {
            respuesta.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            respuesta.setHeader("WWW-Authenticate", "Bearer realm=\"mediconecta\"");
            respuesta.setContentType("application/json;charset=UTF-8");
            respuesta.getWriter().write("{\"error\":\"" + mensaje.replace("\"", "'")
                    + "\",\"codigo\":401}");
            respuesta.flushBuffer();
            return AuthenticationStatus.SEND_FAILURE;
        } catch (IOException e) {
            return contexto.responseUnauthorized();
        }
    }
}
