package com.mediconecta.seguridad;

/** El token JWT recibido no es valido: mal formado, firma incorrecta o vencido. */
public class TokenInvalidoException extends RuntimeException {

    public TokenInvalidoException(String mensaje) {
        super(mensaje);
    }
}
