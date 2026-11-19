package com.mediconecta.seguridad;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ventana deslizante: cuenta eventos por clave (usuario, IP) y dice si la clave
 * ya supero el maximo permitido dentro de la ventana.
 *
 * <p>Es memoria del proceso, sin base de datos: alcanza para un unico servidor
 * (el despliegue del TP). En un cluster habria que compartir los contadores
 * (Infinispan, Redis); la interfaz de uso no cambiaria.</p>
 *
 * <p>Es seguro para hilos: cada clave tiene su propia cola y se sincroniza
 * solo sobre ella.</p>
 */
public class LimitadorDeIntentos {

    /** Por encima de este tamano se purgan las claves sin eventos vigentes. */
    private static final int CLAVES_ANTES_DE_PURGAR = 10_000;

    private final int maximo;

    private final Duration ventana;

    private final Clock reloj;

    private final Map<String, Deque<Instant>> eventos = new ConcurrentHashMap<>();

    public LimitadorDeIntentos(int maximo, Duration ventana, Clock reloj) {
        if (maximo < 1 || ventana.isNegative() || ventana.isZero()) {
            throw new IllegalArgumentException("El limite y la ventana deben ser positivos.");
        }
        this.maximo = maximo;
        this.ventana = ventana;
        this.reloj = reloj;
    }

    /**
     * @return segundos que faltan para que la clave vuelva a estar habilitada,
     *         o {@code 0} si todavia no llego al maximo
     */
    public long segundosDeBloqueo(String clave) {
        Deque<Instant> cola = eventos.get(clave);
        if (cola == null) {
            return 0;
        }
        Instant ahora = reloj.instant();
        synchronized (cola) {
            descartarVencidos(cola, ahora);
            if (cola.size() < maximo) {
                return 0;
            }
            Instant liberacion = cola.peekFirst().plus(ventana);
            return Math.max(1, Duration.between(ahora, liberacion).toSeconds());
        }
    }

    /** Anota un evento (un intento fallido, un pedido). */
    public void registrar(String clave) {
        if (eventos.size() > CLAVES_ANTES_DE_PURGAR) {
            purgar();
        }
        Instant ahora = reloj.instant();
        Deque<Instant> cola = eventos.computeIfAbsent(clave, k -> new ArrayDeque<>());
        synchronized (cola) {
            descartarVencidos(cola, ahora);
            cola.addLast(ahora);
        }
    }

    /**
     * Para limitar pedidos: si la clave esta habilitada, cuenta este pedido y
     * devuelve {@code 0}; si no, devuelve la espera sin contarlo.
     */
    public long consumir(String clave) {
        long espera = segundosDeBloqueo(clave);
        if (espera == 0) {
            registrar(clave);
        }
        return espera;
    }

    /** Olvida los eventos de la clave (por ejemplo, despues de un login correcto). */
    public void olvidar(String clave) {
        eventos.remove(clave);
    }

    private void descartarVencidos(Deque<Instant> cola, Instant ahora) {
        Instant limite = ahora.minus(ventana);
        while (!cola.isEmpty() && !cola.peekFirst().isAfter(limite)) {
            cola.pollFirst();
        }
    }

    private void purgar() {
        Instant ahora = reloj.instant();
        eventos.entrySet().removeIf(entrada -> {
            Deque<Instant> cola = entrada.getValue();
            synchronized (cola) {
                descartarVencidos(cola, ahora);
                return cola.isEmpty();
            }
        });
    }
}
