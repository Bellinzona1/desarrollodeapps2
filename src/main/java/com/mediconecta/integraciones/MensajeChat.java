package com.mediconecta.integraciones;

/**
 * Un mensaje de la conversacion con el asistente de orientacion.
 *
 * <p>La conversacion no se guarda en el servidor: el navegador la manda
 * completa en cada pedido y el servidor la valida antes de reenviarla al
 * modelo de IA.</p>
 */
public class MensajeChat {

    /** Lo escribio la persona que esta reservando. */
    public static final String USUARIO = "usuario";

    /** Lo respondio el asistente de IA. */
    public static final String ASISTENTE = "asistente";

    private String rol;

    private String texto;

    public MensajeChat() {
    }

    public MensajeChat(String rol, String texto) {
        this.rol = rol;
        this.texto = texto;
    }

    public String getRol() {
        return rol;
    }

    public void setRol(String rol) {
        this.rol = rol;
    }

    public String getTexto() {
        return texto;
    }

    public void setTexto(String texto) {
        this.texto = texto;
    }
}
