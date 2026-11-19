package com.mediconecta.integraciones;

import com.mediconecta.negocio.IntegracionExternaException;

import jakarta.annotation.Resource;
import jakarta.enterprise.concurrent.ManagedExecutorService;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.logging.Logger;

/**
 * Resiliencia de las integraciones externas (SOAP legado, partner REST, IA).
 *
 * <p>Cada llamada a un sistema externo pasa por {@link #ejecutar} y queda
 * protegida por tres mecanismos:</p>
 * <ol>
 *   <li><b>Timeout de {@value #SEGUNDOS_TIMEOUT} segundos.</b> La llamada corre
 *       en el {@code ManagedExecutorService} del contenedor y se la espera como
 *       maximo ese tiempo, sin importar que haga la biblioteca cliente por
 *       debajo. Ademas cada adapter configura el mismo limite en su cliente HTTP
 *       o SOAP, para que el hilo de fondo tambien se libere.</li>
 *   <li><b>Circuit breaker.</b> Despues de {@value #FALLOS_PARA_ABRIR} fallas
 *       seguidas el circuito se <i>abre</i> por 30 segundos: durante ese tiempo
 *       no se llama al sistema caido y se responde con el fallback al instante,
 *       en lugar de hacer esperar 5 segundos a cada usuario. Pasado ese tiempo
 *       deja pasar una llamada de prueba (<i>semiabierto</i>): si sale bien se
 *       cierra; si falla vuelve a abrirse.</li>
 *   <li><b>Fallback.</b> Ante cualquier falla, timeout o circuito abierto se
 *       devuelve lo que calcule la funcion alternativa del llamador (el ultimo
 *       dato bueno, una respuesta degradada, un orientador por reglas).</li>
 * </ol>
 *
 * <p>Es {@code @ApplicationScoped} porque el estado del circuito tiene que ser
 * compartido por todos los pedidos.</p>
 */
@ApplicationScoped
public class ProteccionIntegracion {

    private static final Logger LOG = Logger.getLogger(ProteccionIntegracion.class.getName());

    static final int SEGUNDOS_TIMEOUT = 5;

    static final int FALLOS_PARA_ABRIR = 3;

    static final Duration TIEMPO_ABIERTO = Duration.ofSeconds(30);

    /** Estado observable de un circuito. */
    public enum Estado { CERRADO, ABIERTO, SEMIABIERTO }

    @Resource
    private ManagedExecutorService ejecutorDelContenedor;

    private final ExecutorService ejecutorPropio;

    private final Clock reloj;

    private final Duration timeout;

    private final Map<String, Circuito> circuitos = new ConcurrentHashMap<>();

    public ProteccionIntegracion() {
        this(Clock.systemUTC(), null, Duration.ofSeconds(SEGUNDOS_TIMEOUT));
    }

    /**
     * Constructor para los tests: fuera del contenedor no hay
     * {@code ManagedExecutorService}, se usa un ejecutor propio.
     */
    public ProteccionIntegracion(Clock reloj, ExecutorService ejecutor, Duration timeout) {
        this.reloj = reloj;
        this.ejecutorPropio = ejecutor;
        this.timeout = timeout;
    }

    /**
     * Ejecuta la llamada externa con timeout y circuit breaker.
     *
     * @param sistema     nombre del sistema externo (un circuito por sistema)
     * @param llamada     la invocacion al adapter
     * @param alternativa fallback; recibe la falla que lo provoco
     */
    public <T> T ejecutar(String sistema, Callable<T> llamada,
                          Function<IntegracionExternaException, T> alternativa) {
        Circuito circuito = circuitos.computeIfAbsent(sistema, s -> new Circuito());
        if (!circuito.permiteLlamar(reloj.instant())) {
            LOG.fine(() -> "[RESILIENCIA] circuito abierto para " + sistema + ": fallback inmediato");
            return alternativa.apply(new IntegracionExternaException(
                    "El sistema externo " + sistema + " esta temporalmente fuera de servicio."));
        }
        try {
            T resultado = conTimeout(sistema, llamada);
            circuito.exito();
            return resultado;
        } catch (IntegracionExternaException falla) {
            boolean abrio = circuito.fallo(reloj.instant());
            LOG.warning("[RESILIENCIA] " + sistema + " fallo: " + falla.getMessage()
                    + (abrio ? " -> circuito ABIERTO por " + TIEMPO_ABIERTO.toSeconds() + " s" : ""));
            return alternativa.apply(falla);
        }
    }

    /** Estado actual del circuito de un sistema (para diagnostico y tests). */
    public Estado estado(String sistema) {
        Circuito circuito = circuitos.get(sistema);
        return circuito == null ? Estado.CERRADO : circuito.estado(reloj.instant());
    }

    private <T> T conTimeout(String sistema, Callable<T> llamada) {
        Future<T> futuro = ejecutor().submit(llamada);
        try {
            return futuro.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException vencido) {
            futuro.cancel(true);
            throw new IntegracionExternaException("El sistema externo " + sistema + " no respondio en "
                    + timeout.toSeconds() + " segundos.", vencido);
        } catch (ExecutionException error) {
            Throwable causa = error.getCause();
            if (causa instanceof IntegracionExternaException externa) {
                throw externa;
            }
            throw new IntegracionExternaException("Fallo la comunicacion con " + sistema + ".", causa);
        } catch (InterruptedException interrumpido) {
            Thread.currentThread().interrupt();
            futuro.cancel(true);
            throw new IntegracionExternaException("Se interrumpio la llamada a " + sistema + ".", interrumpido);
        }
    }

    private ExecutorService ejecutor() {
        if (ejecutorDelContenedor != null) {
            return ejecutorDelContenedor;
        }
        if (ejecutorPropio != null) {
            return ejecutorPropio;
        }
        return Respaldo.EJECUTOR;
    }

    /** Ejecutor de respaldo, solo si no hay uno del contenedor ni uno dado. */
    private static final class Respaldo {
        static final ExecutorService EJECUTOR = Executors.newCachedThreadPool(tarea -> {
            Thread hilo = new Thread(tarea, "mediconecta-integracion");
            hilo.setDaemon(true);
            return hilo;
        });
    }

    /** Contador de fallas consecutivas y momento de apertura de un sistema. */
    private static final class Circuito {

        private int fallosSeguidos;

        private Instant abiertoHasta;

        private boolean pruebaEnCurso;

        synchronized boolean permiteLlamar(Instant ahora) {
            if (abiertoHasta == null) {
                return true;
            }
            if (ahora.isBefore(abiertoHasta) || pruebaEnCurso) {
                return false;
            }
            pruebaEnCurso = true;   // semiabierto: pasa una sola llamada de prueba
            return true;
        }

        synchronized void exito() {
            fallosSeguidos = 0;
            abiertoHasta = null;
            pruebaEnCurso = false;
        }

        /** @return {@code true} si esta falla abrio el circuito */
        synchronized boolean fallo(Instant ahora) {
            fallosSeguidos++;
            boolean abrir = pruebaEnCurso || fallosSeguidos >= FALLOS_PARA_ABRIR;
            pruebaEnCurso = false;
            if (abrir) {
                abiertoHasta = ahora.plus(TIEMPO_ABIERTO);
            }
            return abrir;
        }

        synchronized Estado estado(Instant ahora) {
            if (abiertoHasta == null) {
                return Estado.CERRADO;
            }
            return ahora.isBefore(abiertoHasta) ? Estado.ABIERTO : Estado.SEMIABIERTO;
        }
    }
}
