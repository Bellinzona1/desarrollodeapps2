package com.mediconecta.integraciones;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Fallback de la orientacion: reglas por palabras clave, sin IA y sin red. */
class OrientadorPorPalabrasClaveTest {

    private static final List<String> TODAS =
            List.of("Cardiologia", "Clinica Medica", "Pediatria", "Traumatologia");

    private final OrientadorPorPalabrasClave orientador = new OrientadorPorPalabrasClave();

    private SugerenciaEspecialidad orientar(List<String> especialidades, String... textos) {
        List<MensajeChat> conversacion = new java.util.ArrayList<>();
        for (int i = 0; i < textos.length; i++) {
            conversacion.add(new MensajeChat(i % 2 == 0 ? MensajeChat.USUARIO : MensajeChat.ASISTENTE, textos[i]));
        }
        return orientador.orientar(conversacion, especialidades);
    }

    @Test
    void lasUrgenciasSeDetectanPrimeroYNoSugierenTurno() {
        SugerenciaEspecialidad sugerencia = orientar(TODAS, "Tengo un DOLOR EN EL PECHO muy fuerte y me falta el aire");
        assertTrue(sugerencia.isUrgente());
        assertNull(sugerencia.getEspecialidad());
        assertTrue(sugerencia.getMensaje().contains("107"));
        assertTrue(orientar(TODAS, "Mi papa se desmayó").isUrgente());
    }

    @Test
    void eligeLaEspecialidadConMasCoincidencias() {
        assertEquals("Traumatologia", orientar(TODAS, "Me torcí el tobillo y me duele la rodilla").getEspecialidad());
        assertEquals("Pediatria", orientar(TODAS, "Mi hijo de 3 años tiene tos y fiebre hace dos días, el nene esta decaido")
                .getEspecialidad());
        assertEquals("Cardiologia", orientar(TODAS, "Tengo palpitaciones y la presión alta").getEspecialidad());
    }

    @Test
    void soloMiraLoQueEscribioLaPersona() {
        SugerenciaEspecialidad sugerencia = orientar(TODAS,
                "Hola", "¿Te duele el tobillo o la rodilla?", "No, tengo fiebre y tos");
        assertEquals("Clinica Medica", sugerencia.getEspecialidad());
    }

    @Test
    void lasPistasCoincidenAlComienzoDeUnaPalabra() {
        assertTrue(OrientadorPorPalabrasClave.aparece(" me duele el ojo ", "ojo"));
        assertFalse(OrientadorPorPalabrasClave.aparece(" un auto rojo ", "ojo"));
        assertFalse(OrientadorPorPalabrasClave.aparece(" estos dias ", "tos"));
    }

    @Test
    void siNoReconoceNadaSugiereClinicaMedica() {
        SugerenciaEspecialidad sugerencia = orientar(TODAS, "No me siento bien");
        assertEquals("Clinica Medica", sugerencia.getEspecialidad());
        assertEquals(OrientadorPorPalabrasClave.ORIGEN, sugerencia.getOrigen());
        assertFalse(sugerencia.isUrgente());
    }

    @Test
    void soloSugiereEspecialidadesDeLaLista() {
        SugerenciaEspecialidad sinTrauma = orientar(List.of("Cardiologia", "Clinica Medica"), "Me torci el tobillo");
        assertEquals("Clinica Medica", sinTrauma.getEspecialidad());

        SugerenciaEspecialidad sinNada = orientar(List.of("Cardiologia"), "Me torci el tobillo");
        assertNull(sinNada.getEspecialidad());
        assertTrue(sinNada.getMensaje().contains("lista"));
    }
}
