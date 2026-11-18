package com.mediconecta.integraciones;

import java.util.List;

/**
 * Interfaz objetivo del patron Adapter para el asistente de orientacion.
 *
 * <p>{@link GestorOrientacion} solo conoce esta interfaz. Hoy la implementa
 * {@link AdaptadorIaGroq}; cambiar de proveedor de IA es escribir otro
 * adaptador, sin tocar la logica de negocio ni la API REST.</p>
 */
public interface OrientadorEspecialidad {

    /**
     * Sugiere con que especialidad sacar el turno a partir de la conversacion.
     *
     * @param conversacion   mensajes en orden; el ultimo es de la persona
     * @param especialidades las unicas especialidades que se pueden sugerir
     */
    SugerenciaEspecialidad orientar(List<MensajeChat> conversacion, List<String> especialidades);
}
