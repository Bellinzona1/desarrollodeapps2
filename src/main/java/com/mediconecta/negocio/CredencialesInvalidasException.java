package com.mediconecta.negocio;

import jakarta.ejb.ApplicationException;

/**
 * Usuario o contrasena incorrectos. La presentacion la traduce a un 401.
 *
 * <p>El mensaje es el mismo para "no existe el usuario" y para "la contrasena
 * no coincide", a proposito: distinguirlos le diria a un atacante que nombres de
 * usuario son validos.</p>
 */
@ApplicationException(rollback = false)
public class CredencialesInvalidasException extends RuntimeException {

    public CredencialesInvalidasException() {
        super("Usuario o contrasena incorrectos.");
    }
}
