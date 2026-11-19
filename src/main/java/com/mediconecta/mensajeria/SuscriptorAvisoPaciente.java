package com.mediconecta.mensajeria;

import jakarta.ejb.ActivationConfigProperty;
import jakarta.ejb.MessageDriven;
import jakarta.inject.Inject;
import jakarta.jms.Message;
import jakarta.jms.MessageListener;

import java.util.logging.Logger;

/**
 * Un suscriptor del topico de recordatorios: el que avisaria al paciente.
 *
 * <p>Comparte el topico {@code TopicoRecordatorios} con
 * {@link SuscriptorAvisoProfesional}: ambos reciben una copia independiente
 * del mismo mensaje, que es exactamente lo que distingue a un topico
 * (publicacion/suscripcion) de una cola (punto a punto).</p>
 *
 * <p><b>Suscripcion durable</b> (Entrega 3): el broker guarda los mensajes
 * publicados mientras este suscriptor no esta conectado (el WAR se esta
 * redesplegando, el servidor se reinicio) y se los entrega al volver. Con una
 * suscripcion comun esos avisos se perdian. Una suscripcion durable exige un
 * {@code clientId} unico y, para conservar el orden, una sola sesion.</p>
 */
@MessageDriven(activationConfig = {
        @ActivationConfigProperty(propertyName = "destinationLookup", propertyValue = DestinosJms.TOPICO_RECORDATORIOS),
        @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "jakarta.jms.Topic"),
        @ActivationConfigProperty(propertyName = "acknowledgeMode", propertyValue = "Auto-acknowledge"),
        @ActivationConfigProperty(propertyName = "subscriptionDurability", propertyValue = "Durable"),
        @ActivationConfigProperty(propertyName = "subscriptionName", propertyValue = "AvisoPaciente"),
        @ActivationConfigProperty(propertyName = "clientId", propertyValue = "mediconecta-aviso-paciente"),
        @ActivationConfigProperty(propertyName = "maxSession", propertyValue = "1")
})
public class SuscriptorAvisoPaciente implements MessageListener {

    private static final Logger LOG = Logger.getLogger(SuscriptorAvisoPaciente.class.getName());

    static final String CONSUMIDOR = "aviso-paciente";

    @Inject
    private ConsumidorIdempotente consumidor;

    @Override
    public void onMessage(Message mensaje) {
        MensajeTurno turno = MensajeTurno.leer(mensaje, NotificadorTurnos.TIPO_RECORDATORIO);
        consumidor.procesarUnaVez(CONSUMIDOR, turno.clave(), turno.resumen(),
                () -> LOG.info(() -> "[JMS-TOPICO] recordatorio para el paciente -> " + turno.resumen()));
    }
}
