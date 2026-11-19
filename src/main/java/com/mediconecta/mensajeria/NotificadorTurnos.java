package com.mediconecta.mensajeria;

import com.mediconecta.modelo.Turno;

import jakarta.annotation.Resource;
import jakarta.ejb.Stateless;
import jakarta.inject.Inject;
import jakarta.jms.DeliveryMode;
import jakarta.jms.JMSConnectionFactory;
import jakarta.jms.JMSContext;
import jakarta.jms.Queue;
import jakarta.jms.Topic;

import java.time.format.DateTimeFormatter;

/**
 * Productor JMS: avisa que un turno quedo confirmado.
 *
 * <p>Envia dos mensajes con el mismo hecho de negocio pero con dos patrones de
 * mensajeria distintos:</p>
 * <ul>
 *   <li><b>Cola (punto a punto).</b> {@code ColaConfirmaciones}: la procesa
 *   exactamente un consumidor ({@link ReceptorConfirmacion}), el que en un
 *   sistema real dispararia el mail o el SMS de confirmacion.</li>
 *   <li><b>Topico (publicacion/suscripcion).</b> {@code TopicoRecordatorios}:
 *   la reciben, cada uno por su lado, todos los suscriptores interesados
 *   ({@link SuscriptorAvisoPaciente} y {@link SuscriptorAvisoProfesional}).</li>
 * </ul>
 *
 * <p>Se invoca desde {@link com.mediconecta.negocio.FachadaReservas#reservar}
 * dentro de la misma transaccion JTA que crea el turno. El {@link JMSContext}
 * se obtiene de la connection factory XA ({@code java:/JmsXA}), asi que queda
 * enlistado en esa transaccion: si el commit de la base de datos no llega a
 * ocurrir, ningun mensaje se entrega.</p>
 *
 * <p><b>Entrega 3 - confiabilidad.</b> Los mensajes son {@code PERSISTENT}
 * (se escriben en el journal del broker y sobreviven un reinicio) y llevan la
 * propiedad {@code claveIdempotencia} ({@code turno-15-confirmado}) que usan
 * los consumidores para no procesar dos veces el mismo aviso.</p>
 */
@Stateless
public class NotificadorTurnos {

    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    static final String TIPO_CONFIRMACION = "CONFIRMACION";

    static final String TIPO_RECORDATORIO = "RECORDATORIO";

    @Inject
    @JMSConnectionFactory("java:/JmsXA")
    private JMSContext contexto;

    @Resource(lookup = DestinosJms.COLA_CONFIRMACIONES)
    private Queue colaConfirmaciones;

    @Resource(lookup = DestinosJms.TOPICO_RECORDATORIOS)
    private Topic topicoRecordatorios;

    public void turnoConfirmado(Turno turno) {
        String resumen = String.format(
                "Turno %d: %s con %s (%s) el %s",
                turno.getId(),
                turno.getPaciente().getNombre(),
                turno.getProfesional().getNombre(),
                turno.getEspecialidad(),
                turno.getFechaHora().format(FORMATO));
        String clave = claveDe(turno);

        contexto.createProducer()
                .setDeliveryMode(DeliveryMode.PERSISTENT)
                .setProperty("turnoId", turno.getId())
                .setProperty(MensajeTurno.PROPIEDAD_CLAVE, clave)
                .send(colaConfirmaciones, TIPO_CONFIRMACION + "|" + resumen);

        contexto.createProducer()
                .setDeliveryMode(DeliveryMode.PERSISTENT)
                .setProperty("turnoId", turno.getId())
                .setProperty(MensajeTurno.PROPIEDAD_CLAVE, clave)
                .send(topicoRecordatorios, TIPO_RECORDATORIO + "|" + resumen);
    }

    /** Clave de negocio del aviso: la misma para la cola y el topico (cada consumidor lleva su registro). */
    static String claveDe(Turno turno) {
        return "turno-" + turno.getId() + "-confirmado";
    }
}
