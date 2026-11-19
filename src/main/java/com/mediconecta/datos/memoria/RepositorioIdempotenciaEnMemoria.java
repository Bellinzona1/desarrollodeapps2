package com.mediconecta.datos.memoria;

import com.mediconecta.datos.RepositorioIdempotencia;
import com.mediconecta.modelo.RegistroIdempotencia;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Implementacion en memoria de {@link RepositorioIdempotencia} (plan B sin base
 * de datos y tests unitarios). {@code putIfAbsent} cumple el mismo papel que la
 * restriccion unica de la tabla.
 */
@Alternative
@ApplicationScoped
public class RepositorioIdempotenciaEnMemoria implements RepositorioIdempotencia {

    private final Map<String, RegistroIdempotencia> registros = new ConcurrentHashMap<>();

    private final AtomicLong generadorDeId = new AtomicLong(0);

    private static String llave(String usuario, String clave) {
        return usuario + "|" + clave.length() + "|" + clave;
    }

    @Override
    public Optional<RegistroIdempotencia> buscar(String usuario, String clave) {
        return Optional.ofNullable(registros.get(llave(usuario, clave)));
    }

    @Override
    public boolean registrar(RegistroIdempotencia registro) {
        if (registros.putIfAbsent(llave(registro.getUsuario(), registro.getClave()), registro) != null) {
            return false;
        }
        registro.setId(generadorDeId.incrementAndGet());
        return true;
    }

    @Override
    public RegistroIdempotencia actualizar(RegistroIdempotencia registro) {
        registros.put(llave(registro.getUsuario(), registro.getClave()), registro);
        return registro;
    }

    @Override
    public int borrarAnterioresA(LocalDateTime limite) {
        int antes = registros.size();
        registros.values().removeIf(r -> r.getCreadaEn().isBefore(limite));
        return antes - registros.size();
    }
}
