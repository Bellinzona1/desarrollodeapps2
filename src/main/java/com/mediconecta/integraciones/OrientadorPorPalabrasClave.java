package com.mediconecta.integraciones;

import jakarta.enterprise.inject.Vetoed;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fallback del asistente de orientacion: elige la especialidad por palabras
 * clave, sin IA y sin red.
 *
 * <p>Es la segunda implementacion de {@link OrientadorEspecialidad} (patron
 * Adapter): {@link GestorOrientacion} la usa cuando el proveedor de IA no
 * responde, tarda mas de 5 segundos, esta mal configurado o tiene el circuito
 * abierto. Es deliberadamente conservadora:</p>
 * <ul>
 *   <li>los signos de emergencia se revisan <b>primero</b> y siempre devuelven
 *       {@code urgente = true} con el aviso de llamar al 107 / 911;</li>
 *   <li>solo sugiere especialidades que esten en la lista recibida;</li>
 *   <li>si no reconoce nada sugiere Clinica Medica (si existe) o pide elegir
 *       de la lista; nunca diagnostica.</li>
 * </ul>
 *
 * <p>{@code @Vetoed}: no es un bean CDI. Si lo fuera, habria dos beans del tipo
 * {@code OrientadorEspecialidad} y la inyeccion de {@link GestorOrientacion}
 * quedaria ambigua. Es una clase sin estado y se instancia directamente.</p>
 */
@Vetoed
public class OrientadorPorPalabrasClave implements OrientadorEspecialidad {

    static final String ORIGEN = "REGLAS";

    static final String CLINICA_MEDICA = "Clinica Medica";

    /** Signos de alarma: ante cualquiera, no se sugiere turno sino guardia o emergencias. */
    private static final List<String> SIGNOS_DE_URGENCIA = List.of(
            "dolor de pecho", "dolor en el pecho", "falta de aire", "no puedo respirar", "me ahogo",
            "desmay", "convulsi", "sangrado abundante", "sangra mucho", "boca torcida",
            "no puedo mover", "hacerme dano", "quiero morir", "suicid", "matarme");

    /** Pistas por especialidad (ya normalizadas: minusculas y sin tildes). */
    private static final Map<String, List<String>> PISTAS = pistas();

    private static Map<String, List<String>> pistas() {
        Map<String, List<String>> mapa = new LinkedHashMap<>();
        mapa.put("Pediatria", List.of("bebe", "mi hijo", "mi hija", "nene", "nena", "nino", "nina", "pediatr"));
        mapa.put("Cardiologia", List.of("corazon", "palpitacion", "presion alta", "hipertension", "taquicardia",
                "arritmia", "cardio"));
        mapa.put("Traumatologia", List.of("hueso", "fractura", "esguince", "tobillo", "rodilla", "espalda",
                "columna", "golpe", "torci", "muneca", "hombro", "cadera", "lesion"));
        mapa.put("Dermatologia", List.of("piel", "granos", "acne", "lunar", "picazon", "sarpullido", "mancha"));
        mapa.put("Ginecologia", List.of("embaraz", "menstrua", "ginecolog", "papanicolau", "utero", "ovario"));
        mapa.put("Oftalmologia", List.of("ojo", "vista", "vision", "anteojos"));
        mapa.put(CLINICA_MEDICA, List.of("fiebre", "gripe", "resfrio", "tos", "dolor de cabeza", "cansancio",
                "chequeo", "control general", "analisis", "mareo"));
        return mapa;
    }

    @Override
    public SugerenciaEspecialidad orientar(List<MensajeChat> conversacion, List<String> especialidades) {
        String texto = textoDeLaPersona(conversacion);

        if (SIGNOS_DE_URGENCIA.stream().anyMatch(signo -> aparece(texto, signo))) {
            return sugerencia("Lo que contas puede ser una urgencia: no esperes un turno. Llama al 107 (SAME) "
                    + "o al 911, o anda a la guardia mas cercana.", null, true);
        }

        String elegida = null;
        int mejorPuntaje = 0;
        for (Map.Entry<String, List<String>> pista : PISTAS.entrySet()) {
            String enLaLista = GestorOrientacion.especialidadDeLaLista(pista.getKey(), especialidades);
            if (enLaLista == null) {
                continue;
            }
            int puntaje = (int) pista.getValue().stream().filter(palabra -> aparece(texto, palabra)).count();
            if (puntaje > mejorPuntaje) {
                mejorPuntaje = puntaje;
                elegida = enLaLista;
            }
        }

        if (elegida != null) {
            return sugerencia("El asistente inteligente no esta disponible en este momento. Por lo que contaste, "
                    + "te sugerimos " + elegida + ". Podes confirmarla o elegir otra de la lista.", elegida, false);
        }
        String clinica = GestorOrientacion.especialidadDeLaLista(CLINICA_MEDICA, especialidades);
        if (clinica != null) {
            return sugerencia("El asistente inteligente no esta disponible en este momento. Para una primera "
                    + "consulta te sugerimos " + clinica + ", o elegi otra especialidad de la lista.", clinica, false);
        }
        return sugerencia("El asistente inteligente no esta disponible en este momento. "
                + "Elegi la especialidad de la lista.", null, false);
    }

    private static String textoDeLaPersona(List<MensajeChat> conversacion) {
        StringBuilder texto = new StringBuilder();
        for (MensajeChat mensaje : conversacion) {
            if (MensajeChat.USUARIO.equals(mensaje.getRol()) && mensaje.getTexto() != null) {
                texto.append(' ').append(GestorOrientacion.normalizar(mensaje.getTexto()));
            }
        }
        // Solo letras y numeros, separados por un espacio: "Me torci!" -> " me torci ".
        return texto.toString().replaceAll("[^a-z0-9]+", " ") + " ";
    }

    /** La pista aparece al comienzo de una palabra ("tos" no coincide con "estos"). */
    static boolean aparece(String texto, String pista) {
        return texto.contains(" " + pista);
    }

    private static SugerenciaEspecialidad sugerencia(String mensaje, String especialidad, boolean urgente) {
        SugerenciaEspecialidad sugerencia = new SugerenciaEspecialidad(mensaje, especialidad, urgente);
        sugerencia.setOrigen(ORIGEN);
        return sugerencia;
    }
}
