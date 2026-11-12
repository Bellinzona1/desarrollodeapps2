package com.mediconecta.seguridad;

import java.time.Instant;
import java.util.Set;

/**
 * Lo que la aplicacion necesita de un token ya validado.
 *
 * @param usuario claim {@code sub}
 * @param roles   claim {@code groups} (mismo nombre que usa MicroProfile JWT)
 * @param expira  claim {@code exp}
 */
public record ClaimsJwt(String usuario, Set<String> roles, Instant expira) {
}
