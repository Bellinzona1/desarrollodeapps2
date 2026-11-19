package com.mediconecta.mensajeria;

import com.mediconecta.datos.RepositorioMensajesProcesados;
import com.mediconecta.negocio.Roles;

import jakarta.annotation.Resource;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ejb.Stateless;
import jakarta.inject.Inject;
import jakarta.jms.DeliveryMode;
import jakarta.jms.JMSConnectionFactory;
import jakarta.jms.JMSContext;
import jakarta.jms.Queue;

import java.util.UUID;

/**
 * Diagnostico de la mensajeria para operacion y para la demo (solo ADMIN).
 *
 * <ul>
 *   <li>{@link #estado()}: cuantos mensajes proceso cada consumidor y los
 *       ultimos que terminaron en la DLQ.</li>
 *   <li>{@link #enviarMensajeVeneno()}: publica en la cola de confirmaciones
 *       un mensaje con formato invalido para demostrar el circuito completo de
 *       falla: reintentos del broker -&gt; DLQ -&gt; {@link MonitorColaMuerta}.</li>
 * </ul>
 */
@Stateless
@RolesAllowed(Roles.ADMIN)
public class DiagnosticoMensajeria {

    static final String CUERPO_VENENO = "MENSAJE-INVALIDO|prueba de Dead Letter Queue";

    @Inject
    @JMSConnectionFactory("java:/JmsXA")
    private JMSContext contexto;

    @Resource(lookup = DestinosJms.COLA_CONFIRMACIONES)
    private Queue colaConfirmaciones;

    @Inject
    private RepositorioMensajesProcesados repositorio;

    public EstadoMensajeria estado() {
        return new EstadoMensajeria(repositorio.contarPorConsumidor(),
                repositorio.ultimos(MonitorColaMuerta.CONSUMIDOR, 10));
    }

    /** @return la clave del mensaje enviado, para buscarlo despues en la DLQ */
    public String enviarMensajeVeneno() {
        String clave = "prueba-dlq-" + UUID.randomUUID();
        contexto.createProducer()
                .setDeliveryMode(DeliveryMode.PERSISTENT)
                .setProperty(MensajeTurno.PROPIEDAD_CLAVE, clave)
                .send(colaConfirmaciones, CUERPO_VENENO);
        return clave;
    }
}
