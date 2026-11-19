package com.mediconecta.datos;

import com.mediconecta.modelo.MensajeProcesado;

import java.util.List;
import java.util.Map;

/** Registro de mensajes JMS ya procesados por cada consumidor (patron DAO / Repository). */
public interface RepositorioMensajesProcesados {

    boolean yaProcesado(String consumidor, String clave);

    /**
     * Registra el mensaje y lo escribe en la base en el acto.
     *
     * @return {@code false} si ya estaba registrado (otra entrega del mismo mensaje)
     */
    boolean registrar(MensajeProcesado mensaje);

    /** Cantidad de mensajes procesados por consumidor. */
    Map<String, Long> contarPorConsumidor();

    /** Los ultimos mensajes de un consumidor, del mas nuevo al mas viejo. */
    List<MensajeProcesado> ultimos(String consumidor, int cantidad);
}
