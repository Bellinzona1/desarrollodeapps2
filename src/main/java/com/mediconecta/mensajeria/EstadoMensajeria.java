package com.mediconecta.mensajeria;

import com.mediconecta.modelo.MensajeProcesado;

import java.util.List;
import java.util.Map;

/**
 * Foto de la mensajeria para {@code GET /api/mensajeria/estado}.
 *
 * @param procesadosPorConsumidor mensajes procesados por cada consumidor
 *                                (incluye {@code dlq-confirmaciones})
 * @param ultimosEnDlq            los ultimos mensajes que llegaron a la DLQ
 */
public record EstadoMensajeria(Map<String, Long> procesadosPorConsumidor, List<MensajeProcesado> ultimosEnDlq) {
}
