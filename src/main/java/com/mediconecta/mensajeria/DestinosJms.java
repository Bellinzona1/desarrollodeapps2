package com.mediconecta.mensajeria;

import jakarta.jms.JMSDestinationDefinition;
import jakarta.jms.JMSDestinationDefinitions;

/**
 * Declara los destinos JMS que usa MediConecta de forma portable, en lugar de
 * crearlos a mano con la consola o el CLI de WildFly.
 *
 * <p>Al estar en una anotacion, el contenedor crea estos destinos solo al
 * desplegar el WAR, siempre que el perfil del servidor tenga activo el
 * subsistema de mensajeria ({@code standalone-full.xml} o
 * {@code standalone-full-ha.xml}).</p>
 *
 * <ul>
 *   <li>{@code ColaConfirmaciones}: punto a punto, la consume
 *       {@link ReceptorConfirmacion}.</li>
 *   <li>{@code TopicoRecordatorios}: publicacion/suscripcion, con dos
 *       suscriptores durables.</li>
 *   <li>{@code ColaConfirmacionesDLQ} (Entrega 3): <b>Dead Letter Queue</b>.
 *       Los mensajes de la cola de confirmaciones que fallan todos sus
 *       reintentos terminan aca y los registra {@link MonitorColaMuerta}. La
 *       asociacion cola -&gt; DLQ y la cantidad de reintentos son un
 *       {@code address-setting} del broker: lo crea
 *       {@code scripts/wildfly/configurar-mensajeria.cli}. Sin ese script el
 *       broker igual aplica su configuracion por omision (10 intentos y la DLQ
 *       general {@code jms.queue.DLQ}).</li>
 * </ul>
 */
@JMSDestinationDefinitions({
        @JMSDestinationDefinition(
                name = DestinosJms.COLA_CONFIRMACIONES,
                interfaceName = "jakarta.jms.Queue",
                destinationName = "ColaConfirmacionesMediConecta"),
        @JMSDestinationDefinition(
                name = DestinosJms.TOPICO_RECORDATORIOS,
                interfaceName = "jakarta.jms.Topic",
                destinationName = "TopicoRecordatoriosMediConecta"),
        @JMSDestinationDefinition(
                name = DestinosJms.COLA_CONFIRMACIONES_DLQ,
                interfaceName = "jakarta.jms.Queue",
                destinationName = "ColaConfirmacionesDLQMediConecta")
})
public final class DestinosJms {

    public static final String COLA_CONFIRMACIONES = "java:/jms/queue/ColaConfirmaciones";

    public static final String TOPICO_RECORDATORIOS = "java:/jms/topic/TopicoRecordatorios";

    public static final String COLA_CONFIRMACIONES_DLQ = "java:/jms/queue/ColaConfirmacionesDLQ";

    private DestinosJms() {
    }
}
