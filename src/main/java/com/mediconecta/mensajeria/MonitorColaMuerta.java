package com.mediconecta.mensajeria;

import jakarta.ejb.ActivationConfigProperty;
import jakarta.ejb.MessageDriven;
import jakarta.inject.Inject;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.MessageListener;
import jakarta.jms.TextMessage;

import java.util.logging.Logger;

/**
 * Consumidor de la <b>Dead Letter Queue</b> de confirmaciones.
 *
 * <p>Un mensaje llega aca cuando {@link ReceptorConfirmacion} fallo con el
 * todas las veces que el broker lo intento (mensaje veneno o falla persistente). Este MDB no reintenta nada: deja
 * constancia (log SEVERE y una fila en {@code mensajes_procesados} con
 * consumidor {@value #CONSUMIDOR}) para que un operador lo revise. El estado se
 * consulta con {@code GET /api/mensajeria/estado} (rol ADMIN).</p>
 *
 * <p>Nunca lanza excepciones: si fallara, el mensaje volveria a la DLQ en un
 * ciclo sin fin.</p>
 */
@MessageDriven(activationConfig = {
        @ActivationConfigProperty(propertyName = "destinationLookup", propertyValue = DestinosJms.COLA_CONFIRMACIONES_DLQ),
        @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "jakarta.jms.Queue"),
        @ActivationConfigProperty(propertyName = "acknowledgeMode", propertyValue = "Auto-acknowledge")
})
public class MonitorColaMuerta implements MessageListener {

    private static final Logger LOG = Logger.getLogger(MonitorColaMuerta.class.getName());

    static final String CONSUMIDOR = "dlq-confirmaciones";

    @Inject
    private ConsumidorIdempotente consumidor;

    @Override
    public void onMessage(Message mensaje) {
        String clave = describirClave(mensaje);
        String cuerpo = describirCuerpo(mensaje);
        try {
            consumidor.procesarUnaVez(CONSUMIDOR, clave, cuerpo,
                    () -> LOG.severe("[JMS-DLQ] mensaje sin procesar movido a la DLQ: clave=" + clave + " -> " + cuerpo));
        } catch (RuntimeException error) {
            LOG.severe("[JMS-DLQ] no se pudo registrar el mensaje " + clave + ": " + error.getMessage());
        }
    }

    static String describirClave(Message mensaje) {
        try {
            String clave = mensaje.getStringProperty(MensajeTurno.PROPIEDAD_CLAVE);
            return clave != null && !clave.isBlank() ? clave : String.valueOf(mensaje.getJMSMessageID());
        } catch (JMSException error) {
            return "desconocida";
        }
    }

    static String describirCuerpo(Message mensaje) {
        try {
            return mensaje instanceof TextMessage texto ? String.valueOf(texto.getText()) : mensaje.getClass().getSimpleName();
        } catch (JMSException error) {
            return "(cuerpo ilegible)";
        }
    }
}
