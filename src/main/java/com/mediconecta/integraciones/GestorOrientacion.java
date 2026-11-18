package com.mediconecta.integraciones;

import com.mediconecta.negocio.GestionProfesionales;
import com.mediconecta.negocio.ReglaNegocioException;
import com.mediconecta.negocio.Roles;

import jakarta.annotation.security.RolesAllowed;
import jakarta.ejb.Stateless;
import jakarta.inject.Inject;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

/**
 * Orientacion con IA para el primer paso de la reserva: la persona cuenta con
 * sus palabras que le pasa y el asistente le sugiere una especialidad.
 *
 * <p>La IA solo <b>sugiere</b>: la eleccion la sigue haciendo la persona con
 * el mismo paso del asistente {@code @Stateful} que usa la lista. Ademas, la
 * especialidad sugerida se compara contra las que tienen profesionales
 * activos; si el modelo inventa una, se descarta.</p>
 *
 * <p>Es stateless: no guarda la conversacion, la recibe completa en cada
 * invocacion.</p>
 */
@Stateless
public class GestorOrientacion {

    static final int MAXIMO_MENSAJES = 20;

    static final int MAXIMO_CARACTERES = 1000;

    @Inject
    private GestionProfesionales gestionProfesionales;

    @Inject
    private OrientadorEspecialidad orientador;

    @RolesAllowed({Roles.PACIENTE, Roles.RECEPCION, Roles.ADMIN})
    public SugerenciaEspecialidad sugerirEspecialidad(List<MensajeChat> conversacion) {
        validar(conversacion);

        List<String> especialidades = gestionProfesionales.listarEspecialidades();
        if (especialidades.isEmpty()) {
            throw new ReglaNegocioException("No hay especialidades con profesionales activos.");
        }

        SugerenciaEspecialidad sugerencia = orientador.orientar(conversacion, especialidades);
        sugerencia.setEspecialidad(especialidadDeLaLista(sugerencia.getEspecialidad(), especialidades));
        return sugerencia;
    }

    static void validar(List<MensajeChat> conversacion) {
        if (conversacion == null || conversacion.isEmpty()) {
            throw new ReglaNegocioException("Conta que te pasa para poder orientarte.");
        }
        if (conversacion.size() > MAXIMO_MENSAJES) {
            throw new ReglaNegocioException(
                    "La conversacion es demasiado larga. Elegi la especialidad de la lista o empeza de nuevo.");
        }
        for (MensajeChat mensaje : conversacion) {
            if (mensaje == null || mensaje.getTexto() == null || mensaje.getTexto().isBlank()) {
                throw new ReglaNegocioException("La conversacion tiene un mensaje vacio.");
            }
            if (!MensajeChat.USUARIO.equals(mensaje.getRol()) && !MensajeChat.ASISTENTE.equals(mensaje.getRol())) {
                throw new ReglaNegocioException("Cada mensaje tiene que ser del usuario o del asistente.");
            }
            if (mensaje.getTexto().length() > MAXIMO_CARACTERES) {
                throw new ReglaNegocioException(
                        "Cada mensaje puede tener hasta " + MAXIMO_CARACTERES + " caracteres.");
            }
        }
        if (!MensajeChat.USUARIO.equals(conversacion.get(conversacion.size() - 1).getRol())) {
            throw new ReglaNegocioException("El ultimo mensaje tiene que ser tuyo.");
        }
    }

    /**
     * Devuelve la especialidad tal como esta escrita en la lista, o
     * {@code null} si la sugerida no esta. Ignora mayusculas y tildes:
     * "Cardiología" encuentra "Cardiologia".
     */
    static String especialidadDeLaLista(String sugerida, List<String> especialidades) {
        if (sugerida == null) {
            return null;
        }
        String buscada = normalizar(sugerida);
        return especialidades.stream()
                .filter(especialidad -> normalizar(especialidad).equals(buscada))
                .findFirst()
                .orElse(null);
    }

    private static String normalizar(String texto) {
        return Normalizer.normalize(texto.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }
}
