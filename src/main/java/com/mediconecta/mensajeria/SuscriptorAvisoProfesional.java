package com.mediconecta.mensajeria;

import jakarta.ejb.ActivationConfigProperty;
import jakarta.ejb.MessageDriven;
import jakarta.inject.Inject;
import jakarta.jms.Message;
import jakarta.jms.MessageListener;

import java.util.logging.Logger;

/**
 * El otro suscriptor del topico de recordatorios: el que sincronizaria la
 * agenda del profesional.
 *
 * <p>Recibe su propia copia del mismo mensaje que procesa
 * {@link SuscriptorAvisoPaciente}, de forma independiente, con su propia
 * suscripcion durable y su propio registro de idempotencia. Ver esa clase para
 * la explicacion del patron publicacion/suscripcion.</p>
 */
@MessageDriven(activationConfig = {
        @ActivationConfigProperty(propertyName = "destinationLookup", propertyValue = DestinosJms.TOPICO_RECORDATORIOS),
        @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "jakarta.jms.Topic"),
        @ActivationConfigProperty(propertyName = "acknowledgeMode", propertyValue = "Auto-acknowledge"),
        @ActivationConfigProperty(propertyName = "subscriptionDurability", propertyValue = "Durable"),
        @ActivationConfigProperty(propertyName = "subscriptionName", propertyValue = "AvisoProfesional"),
        @ActivationConfigProperty(propertyName = "clientId", propertyValue = "mediconecta-aviso-profesional"),
        @ActivationConfigProperty(propertyName = "maxSession", propertyValue = "1")
})
public class SuscriptorAvisoProfesional implements MessageListener {

    private static final Logger LOG = Logger.getLogger(SuscriptorAvisoProfesional.class.getName());

    static final String CONSUMIDOR = "aviso-profesional";

    @Inject
    private ConsumidorIdempotente consumidor;

    @Override
    public void onMessage(Message mensaje) {
        MensajeTurno turno = MensajeTurno.leer(mensaje, NotificadorTurnos.TIPO_RECORDATORIO);
        consumidor.procesarUnaVez(CONSUMIDOR, turno.clave(), turno.resumen(),
                () -> LOG.info(() -> "[JMS-TOPICO] recordatorio para el profesional -> " + turno.resumen()));
    }
}
