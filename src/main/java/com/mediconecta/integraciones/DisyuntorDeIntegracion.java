package com.mediconecta.integraciones;

import jakarta.enterprise.context.ApplicationScoped;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Patron Circuit Breaker (disyuntor) para los sistemas externos: el legado por
 * SOAP, el partner por REST y el proveedor de IA.
 *
 * <p>El timeout de 5 s evita que un pedido quede colgado, pero si el sistema
 * externo esta caido cada pedido igual espera esos 5 s antes de caer en el
 * fallback. El disyuntor corta eso: despues de {@link #FALLAS_PARA_ABRIR}
 * fallas seguidas se <b>abre</b> y durante {@link #ESPERA_ABIERTO} ni siquiera
 * se intenta la llamada; se responde enseguida con el respaldo. Pasado ese
 * tiempo queda <b>semiabierto</b>: deja pasar un unico pedido de prueba. Si sale
 * bien se <b>cierra</b> (vuelve a la normalidad); si falla, se abre otra vez.</p>
 *
 * <pre>
 *   CERRADO --3 fallas seguidas--&gt; ABIERTO --30 s--&gt; SEMIABIERTO --exito--&gt; CERRADO
 *                                    ^                      |
 *                                    +-------falla----------+
 * </pre>
 *
 * <p>Hay un circuito independiente por sistema externo, compartido por todos
 * los pedidos de la aplicacion (por eso es {@code @ApplicationScoped}).</p>
 */
@ApplicationScoped
public class DisyuntorDeIntegracion {

    /** Estado de un circuito. */
    public enum Estado {
        /** Funciona normal: las llamadas pasan. */
        CERRADO,
        /** El sistema fallo varias veces seguidas: no se lo llama y se usa el respaldo. */
        ABIERTO,
        /** Paso el tiempo de espera: se deja pasar un pedido de prueba. */
        SEMIABIERTO
    }

    static final int FALLAS_PARA_ABRIR = 3;
    static final Duration ESPERA_ABIERTO = Duration.ofSeconds(30);

    /** Nombres de los circuitos (uno por sistema externo). */
    public static final String SOAP_LEGADO = "obra-social-legado-soap";
    public static final String REST_PARTNER = "obra-social-partner-rest";
    public static final String IA = "asistente-ia";

    private final Map<String, Circuito> circuitos = new ConcurrentHashMap<>();

    private Clock reloj = Clock.systemUTC();

    /** {@code true} si se puede llamar al sistema; {@code false} si el circuito esta abierto. */
    public boolean permiteLlamar(String sistema) {
        return circuito(sistema).permite(reloj.instant());
    }

    public void registrarExito(String sistema) {
        circuito(sistema).exito();
    }

    public void registrarFalla(String sistema) {
        circuito(sistema).falla(reloj.instant());
    }

    public Estado estado(String sistema) {
        return circuito(sistema).estado(reloj.instant());
    }

    /** Estado de todos los circuitos usados hasta ahora, ordenados por nombre. */
    public Map<String, Estado> estados() {
        Map<String, Estado> estados = new TreeMap<>();
        circuitos.forEach((sistema, circuito) -> estados.put(sistema, circuito.estado(reloj.instant())));
        return estados;
    }

    private Circuito circuito(String sistema) {
        return circuitos.computeIfAbsent(sistema, s -> new Circuito());
    }

    /** Un circuito. Sincronizado: lo comparten todos los hilos del servidor. */
    private static final class Circuito {

        private Estado estado = Estado.CERRADO;
        private int fallasSeguidas;
        private Instant abiertoHasta = Instant.MIN;
        /** Momento en que salio el pedido de prueba del estado semiabierto (null si no hay). */
        private Instant pruebaDesde;

        synchronized boolean permite(Instant ahora) {
            if (estado == Estado.CERRADO) {
                return true;
            }
            if (estado == Estado.ABIERTO) {
                if (ahora.isBefore(abiertoHasta)) {
                    return false;
                }
                estado = Estado.SEMIABIERTO;
                pruebaDesde = ahora;
                return true;
            }
            // Semiabierto: un solo pedido de prueba a la vez. Si el anterior nunca
            // informo el resultado, despues de la espera se permite otro.
            if (pruebaDesde != null && ahora.isBefore(pruebaDesde.plus(ESPERA_ABIERTO))) {
                return false;
            }
            pruebaDesde = ahora;
            return true;
        }

        synchronized void exito() {
            estado = Estado.CERRADO;
            fallasSeguidas = 0;
            pruebaDesde = null;
        }

        synchronized void falla(Instant ahora) {
            fallasSeguidas++;
            if (estado == Estado.SEMIABIERTO || fallasSeguidas >= FALLAS_PARA_ABRIR) {
                estado = Estado.ABIERTO;
                abiertoHasta = ahora.plus(ESPERA_ABIERTO);
                pruebaDesde = null;
            }
        }

        synchronized Estado estado(Instant ahora) {
            if (estado == Estado.ABIERTO && !ahora.isBefore(abiertoHasta)) {
                return Estado.SEMIABIERTO;
            }
            return estado;
        }
    }
}
