package com.mediconecta.mensajeria;

import com.mediconecta.datos.memoria.RepositorioMensajesProcesadosEnMemoria;
import com.mediconecta.modelo.Paciente;
import com.mediconecta.modelo.Profesional;
import com.mediconecta.modelo.Turno;
import com.mediconecta.negocio.Inyeccion;

import jakarta.jms.DeliveryMode;
import jakarta.jms.Destination;
import jakarta.jms.JMSContext;
import jakarta.jms.JMSException;
import jakarta.jms.JMSProducer;
import jakarta.jms.Message;
import jakarta.jms.Queue;
import jakarta.jms.TextMessage;
import jakarta.jms.Topic;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mensajeria sin broker: productor, consumidores idempotentes, mensajes veneno
 * y DLQ. Los objetos JMS son proxies de las interfaces de la API.
 */
class MensajeriaTest {

    private RepositorioMensajesProcesadosEnMemoria repositorio;

    private ConsumidorIdempotente consumidor;

    @BeforeEach
    void preparar() {
        repositorio = new RepositorioMensajesProcesadosEnMemoria();
        consumidor = new ConsumidorIdempotente();
        Inyeccion.campo(consumidor, "repositorio", repositorio);
    }

    // ------------------------------------------------------------ dobles JMS

    /** Un TextMessage de prueba con cuerpo y propiedades. */
    static TextMessage texto(String cuerpo, String clave, String idBroker) {
        Map<String, Object> propiedades = new HashMap<>();
        if (clave != null) {
            propiedades.put(MensajeTurno.PROPIEDAD_CLAVE, clave);
        }
        return (TextMessage) Proxy.newProxyInstance(MensajeriaTest.class.getClassLoader(),
                new Class<?>[]{TextMessage.class}, (proxy, metodo, args) -> switch (metodo.getName()) {
                    case "getText" -> cuerpo;
                    case "getStringProperty" -> (String) propiedades.get((String) args[0]);
                    case "getJMSMessageID" -> idBroker;
                    case "hashCode" -> 1;
                    case "equals" -> proxy == args[0];
                    default -> null;
                });
    }

    static Message noTexto() {
        return (Message) Proxy.newProxyInstance(MensajeriaTest.class.getClassLoader(),
                new Class<?>[]{Message.class}, (proxy, metodo, args) -> switch (metodo.getName()) {
                    case "getStringProperty" -> null;
                    case "getJMSMessageID" -> "ID:1";
                    default -> null;
                });
    }

    static TextMessage ilegible() {
        return (TextMessage) Proxy.newProxyInstance(MensajeriaTest.class.getClassLoader(),
                new Class<?>[]{TextMessage.class}, (proxy, metodo, args) -> {
                    throw new JMSException("broker caido");
                });
    }

    /** Lo que se envio por el JMSContext de prueba. */
    record Envio(Destination destino, String cuerpo, Map<String, Object> propiedades, int modo) {
    }

    static JMSContext contextoQueAnota(List<Envio> envios) {
        return (JMSContext) Proxy.newProxyInstance(MensajeriaTest.class.getClassLoader(),
                new Class<?>[]{JMSContext.class}, (proxy, metodo, args) -> {
                    if (!metodo.getName().equals("createProducer")) {
                        return null;
                    }
                    Map<String, Object> propiedades = new HashMap<>();
                    int[] modo = {DeliveryMode.NON_PERSISTENT};
                    return Proxy.newProxyInstance(MensajeriaTest.class.getClassLoader(),
                            new Class<?>[]{JMSProducer.class}, (productor, m, a) -> {
                                switch (m.getName()) {
                                    case "setDeliveryMode" -> modo[0] = (Integer) a[0];
                                    case "setProperty" -> propiedades.put((String) a[0], a[1]);
                                    case "send" -> envios.add(new Envio((Destination) a[0], (String) a[1],
                                            propiedades, modo[0]));
                                    default -> {
                                        return null;
                                    }
                                }
                                return productor;
                            });
                });
    }

    private static <T> T destino(Class<T> tipo, String nombre) {
        return tipo.cast(Proxy.newProxyInstance(MensajeriaTest.class.getClassLoader(), new Class<?>[]{tipo},
                (proxy, metodo, args) -> metodo.getName().equals("toString") ? nombre : null));
    }

    private static Turno turno() {
        Turno turno = new Turno();
        turno.setId(15L);
        turno.setPaciente(new Paciente("Ana Gomez", "30111222"));
        turno.setProfesional(new Profesional("Dra. Laura Fernandez", "Cardiologia", "MP-1"));
        turno.setEspecialidad("Cardiologia");
        turno.setFechaHora(LocalDateTime.of(2026, 11, 23, 9, 30));
        return turno;
    }

    // -------------------------------------------------------------- productor

    @Test
    void elProductorEnviaALaColaYAlTopicoMensajesPersistentesConClave() {
        List<Envio> envios = new ArrayList<>();
        NotificadorTurnos notificador = new NotificadorTurnos();
        Queue cola = destino(Queue.class, "cola");
        Topic topico = destino(Topic.class, "topico");
        Inyeccion.campo(notificador, "contexto", contextoQueAnota(envios));
        Inyeccion.campo(notificador, "colaConfirmaciones", cola);
        Inyeccion.campo(notificador, "topicoRecordatorios", topico);

        notificador.turnoConfirmado(turno());

        assertEquals(2, envios.size());
        assertSame(cola, envios.get(0).destino());
        assertSame(topico, envios.get(1).destino());
        assertTrue(envios.get(0).cuerpo().startsWith("CONFIRMACION|Turno 15: Ana Gomez con Dra. Laura Fernandez"));
        assertTrue(envios.get(1).cuerpo().startsWith("RECORDATORIO|"));
        for (Envio envio : envios) {
            assertEquals(DeliveryMode.PERSISTENT, envio.modo());
            assertEquals("turno-15-confirmado", envio.propiedades().get(MensajeTurno.PROPIEDAD_CLAVE));
            assertEquals(15L, envio.propiedades().get("turnoId"));
        }
    }

    @Test
    void elDiagnosticoMandaUnMensajeVenenoYMuestraElEstado() {
        List<Envio> envios = new ArrayList<>();
        DiagnosticoMensajeria diagnostico = new DiagnosticoMensajeria();
        Inyeccion.campo(diagnostico, "contexto", contextoQueAnota(envios));
        Inyeccion.campo(diagnostico, "colaConfirmaciones", destino(Queue.class, "cola"));
        Inyeccion.campo(diagnostico, "repositorio", repositorio);

        String clave = diagnostico.enviarMensajeVeneno();
        assertTrue(clave.startsWith("prueba-dlq-"));
        assertEquals(DiagnosticoMensajeria.CUERPO_VENENO, envios.get(0).cuerpo());
        assertThrows(MensajeTurno.MensajeInvalidoException.class,
                () -> MensajeTurno.leer(texto(envios.get(0).cuerpo(), clave, "ID:9"), "CONFIRMACION"));

        consumidor.procesarUnaVez(MonitorColaMuerta.CONSUMIDOR, clave, "veneno", () -> { });
        EstadoMensajeria estado = diagnostico.estado();
        assertEquals(1L, estado.procesadosPorConsumidor().get(MonitorColaMuerta.CONSUMIDOR));
        assertEquals(clave, estado.ultimosEnDlq().get(0).getClave());
    }

    // ------------------------------------------------------- formato y veneno

    @Test
    void leeUnMensajeValido() {
        MensajeTurno mensaje = MensajeTurno.leer(texto("CONFIRMACION|Turno 1", "turno-1-confirmado", "ID:1"),
                "CONFIRMACION");
        assertEquals("turno-1-confirmado", mensaje.clave());
        assertEquals("Turno 1", mensaje.resumen());
    }

    @Test
    void sinClaveDeNegocioUsaElIdDelBroker() {
        assertEquals("ID:7", MensajeTurno.leer(texto("CONFIRMACION|x", null, "ID:7"), "CONFIRMACION").clave());
        assertThrows(MensajeTurno.MensajeInvalidoException.class,
                () -> MensajeTurno.leer(texto("CONFIRMACION|x", " ", null), "CONFIRMACION"));
    }

    @Test
    void losMensajesMalFormadosSonVeneno() {
        assertThrows(MensajeTurno.MensajeInvalidoException.class, () -> MensajeTurno.leer(noTexto(), "CONFIRMACION"));
        assertThrows(MensajeTurno.MensajeInvalidoException.class,
                () -> MensajeTurno.leer(texto(null, "k", "ID:1"), "CONFIRMACION"));
        assertThrows(MensajeTurno.MensajeInvalidoException.class,
                () -> MensajeTurno.leer(texto("RECORDATORIO|x", "k", "ID:1"), "CONFIRMACION"));
        assertThrows(MensajeTurno.MensajeInvalidoException.class,
                () -> MensajeTurno.leer(texto("CONFIRMACION|", "k", "ID:1"), "CONFIRMACION"));
        assertThrows(MensajeTurno.MensajeInvalidoException.class, () -> MensajeTurno.leer(ilegible(), "CONFIRMACION"));
    }

    // ------------------------------------------------- consumidores idempotentes

    @Test
    void cadaConsumidorAplicaElEfectoUnaSolaVez() {
        AtomicInteger efectos = new AtomicInteger();
        assertTrue(consumidor.procesarUnaVez("c1", "k", "detalle", efectos::incrementAndGet));
        assertFalse(consumidor.procesarUnaVez("c1", "k", "detalle", efectos::incrementAndGet));
        assertTrue(consumidor.procesarUnaVez("c2", "k", "x".repeat(400), efectos::incrementAndGet));
        assertEquals(2, efectos.get());
        assertEquals(300, repositorio.ultimos("c2", 1).get(0).getDetalle().length());
    }

    @Test
    void unaEntregaSimultaneaFallaParaQueElBrokerLaReentregue() {
        RepositorioMensajesProcesadosEnMemoria siempreDuplicado = new RepositorioMensajesProcesadosEnMemoria() {
            @Override
            public boolean yaProcesado(String c, String k) {
                return false;
            }

            @Override
            public boolean registrar(com.mediconecta.modelo.MensajeProcesado m) {
                return false;
            }
        };
        Inyeccion.campo(consumidor, "repositorio", siempreDuplicado);
        AtomicInteger efectos = new AtomicInteger();
        assertThrows(IllegalStateException.class,
                () -> consumidor.procesarUnaVez("c", "k", null, efectos::incrementAndGet));
        assertEquals(0, efectos.get());
    }

    @Test
    void elReceptorDeLaColaEsIdempotenteYRechazaElVeneno() {
        ReceptorConfirmacion receptor = new ReceptorConfirmacion();
        Inyeccion.campo(receptor, "consumidor", consumidor);

        receptor.onMessage(texto("CONFIRMACION|Turno 15", "turno-15-confirmado", "ID:1"));
        receptor.onMessage(texto("CONFIRMACION|Turno 15", "turno-15-confirmado", "ID:2"));
        assertEquals(1L, repositorio.contarPorConsumidor().get(ReceptorConfirmacion.CONSUMIDOR));

        assertThrows(MensajeTurno.MensajeInvalidoException.class,
                () -> receptor.onMessage(texto(DiagnosticoMensajeria.CUERPO_VENENO, "prueba", "ID:3")));
    }

    @Test
    void losDosSuscriptoresDelTopicoLlevanRegistrosIndependientes() {
        SuscriptorAvisoPaciente paciente = new SuscriptorAvisoPaciente();
        SuscriptorAvisoProfesional profesional = new SuscriptorAvisoProfesional();
        Inyeccion.campo(paciente, "consumidor", consumidor);
        Inyeccion.campo(profesional, "consumidor", consumidor);
        TextMessage aviso = texto("RECORDATORIO|Turno 15", "turno-15-confirmado", "ID:1");

        paciente.onMessage(aviso);
        profesional.onMessage(aviso);
        paciente.onMessage(aviso);

        Map<String, Long> cuentas = repositorio.contarPorConsumidor();
        assertEquals(1L, cuentas.get(SuscriptorAvisoPaciente.CONSUMIDOR));
        assertEquals(1L, cuentas.get(SuscriptorAvisoProfesional.CONSUMIDOR));
        assertThrows(MensajeTurno.MensajeInvalidoException.class,
                () -> profesional.onMessage(texto("CONFIRMACION|x", "k", "ID:5")));
    }

    @Test
    void elMonitorDeLaDlqRegistraYNuncaLanzaExcepciones() {
        MonitorColaMuerta monitor = new MonitorColaMuerta();
        Inyeccion.campo(monitor, "consumidor", consumidor);

        monitor.onMessage(texto(DiagnosticoMensajeria.CUERPO_VENENO, "prueba-1", "ID:1"));
        monitor.onMessage(texto(DiagnosticoMensajeria.CUERPO_VENENO, "prueba-1", "ID:1"));
        assertDoesNotThrow(() -> monitor.onMessage(noTexto()));
        assertDoesNotThrow(() -> monitor.onMessage(ilegible()));
        assertEquals(3L, repositorio.contarPorConsumidor().get(MonitorColaMuerta.CONSUMIDOR));

        Inyeccion.campo(consumidor, "repositorio", null);
        assertDoesNotThrow(() -> monitor.onMessage(texto("x", "y", "z")));
    }

    @Test
    void describeMensajesDeCualquierTipo() {
        assertEquals("ID:1", MonitorColaMuerta.describirClave(noTexto()));
        assertEquals("desconocida", MonitorColaMuerta.describirClave(ilegible()));
        assertEquals("(cuerpo ilegible)", MonitorColaMuerta.describirCuerpo(ilegible()));
        assertTrue(MonitorColaMuerta.describirCuerpo(noTexto()).length() > 0);
        assertEquals("ID:9", MonitorColaMuerta.describirClave(texto("x", " ", "ID:9")));
    }
}
