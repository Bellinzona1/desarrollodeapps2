package com.mediconecta.mensajeria;

import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.TextMessage;

/**
 * Contenido de un mensaje de turno ya validado.
 *
 * <p>Formato que produce {@link NotificadorTurnos}: cuerpo de texto
 * {@code TIPO|resumen} y la propiedad {@value #PROPIEDAD_CLAVE} con la clave de
 * negocio ({@code turno-15-confirmado}). Un mensaje que no respeta el formato
 * es un <i>mensaje veneno</i>: {@link #leer} lanza
 * {@link MensajeInvalidoException}, la transaccion del MDB se deshace, el
 * broker lo reintenta y, al agotar los intentos, lo mueve a la DLQ.</p>
 *
 * @param clave   clave de idempotencia del mensaje
 * @param resumen texto del aviso
 */
public record MensajeTurno(String clave, String resumen) {

    static final String PROPIEDAD_CLAVE = "claveIdempotencia";

    static MensajeTurno leer(Message mensaje, String tipoEsperado) {
        if (!(mensaje instanceof TextMessage texto)) {
            throw new MensajeInvalidoException("Se esperaba un mensaje de texto.");
        }
        try {
            String cuerpo = texto.getText();
            String prefijo = tipoEsperado + "|";
            if (cuerpo == null || !cuerpo.startsWith(prefijo) || cuerpo.length() == prefijo.length()) {
                throw new MensajeInvalidoException("El mensaje no tiene el formato " + prefijo + "resumen.");
            }
            String clave = mensaje.getStringProperty(PROPIEDAD_CLAVE);
            if (clave == null || clave.isBlank()) {
                // Mensajes anteriores a la Entrega 3, sin clave de negocio: se usa el id del broker.
                clave = mensaje.getJMSMessageID();
            }
            if (clave == null || clave.isBlank()) {
                throw new MensajeInvalidoException("El mensaje no tiene clave de idempotencia.");
            }
            return new MensajeTurno(clave, cuerpo.substring(prefijo.length()));
        } catch (JMSException error) {
            throw new MensajeInvalidoException("No se pudo leer el mensaje: " + error.getMessage());
        }
    }

    /** Mensaje que no se puede procesar; termina en la DLQ despues de los reintentos. */
    public static class MensajeInvalidoException extends RuntimeException {

        public MensajeInvalidoException(String mensaje) {
            super(mensaje);
        }
    }
}
