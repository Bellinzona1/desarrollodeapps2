package com.mediconecta.datos.memoria;

import com.mediconecta.datos.RepositorioMensajesProcesados;
import com.mediconecta.modelo.MensajeProcesado;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/** Implementacion en memoria de {@link RepositorioMensajesProcesados} (plan B y tests). */
@Alternative
@ApplicationScoped
public class RepositorioMensajesProcesadosEnMemoria implements RepositorioMensajesProcesados {

    private final Map<String, MensajeProcesado> mensajes = new ConcurrentHashMap<>();

    private final AtomicLong generadorDeId = new AtomicLong(0);

    private static String llave(String consumidor, String clave) {
        return consumidor + "|" + clave;
    }

    @Override
    public boolean yaProcesado(String consumidor, String clave) {
        return mensajes.containsKey(llave(consumidor, clave));
    }

    @Override
    public boolean registrar(MensajeProcesado mensaje) {
        if (mensajes.putIfAbsent(llave(mensaje.getConsumidor(), mensaje.getClave()), mensaje) != null) {
            return false;
        }
        mensaje.setId(generadorDeId.incrementAndGet());
        return true;
    }

    @Override
    public Map<String, Long> contarPorConsumidor() {
        return mensajes.values().stream()
                .collect(Collectors.groupingBy(MensajeProcesado::getConsumidor, TreeMap::new, Collectors.counting()));
    }

    @Override
    public List<MensajeProcesado> ultimos(String consumidor, int cantidad) {
        return mensajes.values().stream()
                .filter(m -> m.getConsumidor().equals(consumidor))
                .sorted(Comparator.comparing(MensajeProcesado::getId).reversed())
                .limit(cantidad)
                .toList();
    }
}
