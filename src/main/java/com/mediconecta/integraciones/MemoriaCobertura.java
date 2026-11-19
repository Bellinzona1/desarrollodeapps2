package com.mediconecta.integraciones;

import jakarta.enterprise.context.ApplicationScoped;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ultima respuesta buena de cada consulta de cobertura ("stale-if-error").
 *
 * <p>Si la obra social no responde, {@link GestorCobertura} devuelve el ultimo
 * dato que si obtuvo para ese afiliado, marcado como contingencia y con la fecha
 * de la consulta, siempre que no tenga mas de {@value #HORAS_VIGENCIA} horas. La
 * cobertura de un afiliado cambia muy rara vez, asi que es un fallback
 * razonable para no frenar la atencion; el dato dice claramente que no es
 * actual.</p>
 */
@ApplicationScoped
public class MemoriaCobertura {

    static final int HORAS_VIGENCIA = 24;

    private static final DateTimeFormatter FORMATO =
            DateTimeFormatter.ofPattern("dd/MM HH:mm").withZone(ZoneId.systemDefault());

    private record Entrada(ResultadoCobertura resultado, Instant cuando) {
    }

    private final Map<String, Entrada> entradas = new ConcurrentHashMap<>();

    private final Clock reloj;

    public MemoriaCobertura() {
        this(Clock.systemUTC());
    }

    public MemoriaCobertura(Clock reloj) {
        this.reloj = reloj;
    }

    public void recordar(String clave, ResultadoCobertura resultado) {
        entradas.put(clave, new Entrada(resultado, reloj.instant()));
    }

    /** Ultimo dato bueno, ya marcado como contingencia, si no vencio. */
    public Optional<ResultadoCobertura> ultimo(String clave) {
        Entrada entrada = entradas.get(clave);
        if (entrada == null || entrada.cuando().plus(Duration.ofHours(HORAS_VIGENCIA)).isBefore(reloj.instant())) {
            return Optional.empty();
        }
        return Optional.of(entrada.resultado().enContingencia(
                "La obra social no respondio. Dato de la ultima consulta exitosa ("
                        + FORMATO.format(entrada.cuando()) + ")."));
    }
}
