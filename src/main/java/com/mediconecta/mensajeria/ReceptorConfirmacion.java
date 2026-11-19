package com.mediconecta.mensajeria;

import jakarta.ejb.ActivationConfigProperty;
import jakarta.ejb.MessageDriven;
import jakarta.inject.Inject;
import jakarta.jms.Message;
import jakarta.jms.MessageListener;

import java.util.logging.Logger;

/**
 * Consumidor punto a punto de la cola de confirmaciones.
 *
 * <p>Cada mensaje lo procesa una instancia de este MDB, aunque el contenedor
 * mantenga un pool de varias. En un sistema real este seria el componente que
 * dispara el mail o el SMS de confirmacion al paciente; aca se simula con un
 * log para poder demostrarlo sin depender de un proveedor de correo externo.</p>
 *
 * <p><b>Entrega 3 - resiliencia.</b></p>
 * <ul>
 *   <li><b>Idempotente:</b> registra la clave del mensaje en
 *       {@code mensajes_procesados} en la misma transaccion (CMT) en la que lo
 *       procesa; una reentrega del mismo aviso se descarta sin repetir el
 *       efecto ({@link ConsumidorIdempotente}).</li>
 *   <li><b>Mensaje veneno:</b> si el mensaje no tiene el formato esperado se
 *       lanza una excepcion de sistema, la transaccion se deshace y el broker
 *       lo reintenta. Al agotar los intentos (3, con 1 s de espera, segun
 *       {@code configurar-mensajeria.cli}) lo mueve a la DLQ, donde lo registra
 *       {@link MonitorColaMuerta}. Ya no se "traga" el error con un log, que era
 *       perder el mensaje.</li>
 * </ul>
 */
@MessageDriven(activationConfig = {
        @ActivationConfigProperty(propertyName = "destinationLookup", propertyValue = DestinosJms.COLA_CONFIRMACIONES),
        @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "jakarta.jms.Queue"),
        @ActivationConfigProperty(propertyName = "acknowledgeMode", propertyValue = "Auto-acknowledge")
})
public class ReceptorConfirmacion implements MessageListener {

    private static final Logger LOG = Logger.getLogger(ReceptorConfirmacion.class.getName());

    static final String CONSUMIDOR = "confirmaciones";

    @Inject
    private ConsumidorIdempotente consumidor;

    @Override
    public void onMessage(Message mensaje) {
        MensajeTurno turno = MensajeTurno.leer(mensaje, NotificadorTurnos.TIPO_CONFIRMACION);
        consumidor.procesarUnaVez(CONSUMIDOR, turno.clave(), turno.resumen(),
                () -> LOG.info(() -> "[JMS-COLA] confirmacion enviada -> " + turno.resumen()));
    }
}
