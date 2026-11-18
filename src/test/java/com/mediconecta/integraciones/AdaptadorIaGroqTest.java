package com.mediconecta.integraciones;

import com.mediconecta.negocio.IntegracionExternaException;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Traduccion entre MediConecta y el formato "chat completions" del proveedor de IA. */
class AdaptadorIaGroqTest {

    private static String respuestaDelProveedor(String contenido) {
        return Json.createObjectBuilder()
                .add("choices", Json.createArrayBuilder()
                        .add(Json.createObjectBuilder()
                                .add("message", Json.createObjectBuilder()
                                        .add("role", "assistant")
                                        .add("content", contenido))))
                .build()
                .toString();
    }

    @Test
    void elPedidoLlevaLasInstruccionesYLaConversacionConLosRolesDelProveedor() {
        JsonObject pedido = AdaptadorIaGroq.armarPedido("openai/gpt-oss-120b",
                List.of(new MensajeChat(MensajeChat.USUARIO, "Me torci el tobillo"),
                        new MensajeChat(MensajeChat.ASISTENTE, "¿Se te hincho?"),
                        new MensajeChat(MensajeChat.USUARIO, "Si, bastante")),
                List.of("Cardiologia", "Traumatologia"));

        JsonArray mensajes = pedido.getJsonArray("messages");
        assertEquals(4, mensajes.size());
        assertEquals("system", mensajes.getJsonObject(0).getString("role"));
        assertTrue(mensajes.getJsonObject(0).getString("content").contains("- Traumatologia"));
        assertEquals("user", mensajes.getJsonObject(1).getString("role"));
        assertEquals("assistant", mensajes.getJsonObject(2).getString("role"));
        assertEquals("user", mensajes.getJsonObject(3).getString("role"));
        assertEquals("json_object", pedido.getJsonObject("response_format").getString("type"));
        assertEquals("low", pedido.getString("reasoning_effort"));
    }

    @Test
    void soloLosModelosQueRazonanRecibenReasoningEffort() {
        JsonObject pedido = AdaptadorIaGroq.armarPedido("llama-3.3-70b-versatile",
                List.of(new MensajeChat(MensajeChat.USUARIO, "Hola")), List.of("Pediatria"));
        assertFalse(pedido.containsKey("reasoning_effort"));
    }

    @Test
    void leeLaSugerenciaDelJsonQueDevuelveElModelo() {
        SugerenciaEspecialidad sugerencia = AdaptadorIaGroq.interpretarRespuesta(respuestaDelProveedor(
                "{\"mensaje\":\"Te conviene Traumatologia.\",\"especialidad\":\"Traumatologia\",\"urgente\":false}"));

        assertEquals("Te conviene Traumatologia.", sugerencia.getMensaje());
        assertEquals("Traumatologia", sugerencia.getEspecialidad());
        assertFalse(sugerencia.isUrgente());
    }

    @Test
    void unaPreguntaDelAsistenteVieneSinEspecialidad() {
        SugerenciaEspecialidad sugerencia = AdaptadorIaGroq.interpretarRespuesta(respuestaDelProveedor(
                "{\"mensaje\":\"¿Hace cuanto te pasa?\",\"especialidad\":null,\"urgente\":false}"));

        assertEquals("¿Hace cuanto te pasa?", sugerencia.getMensaje());
        assertNull(sugerencia.getEspecialidad());
    }

    @Test
    void marcaLasUrgencias() {
        SugerenciaEspecialidad sugerencia = AdaptadorIaGroq.interpretarRespuesta(respuestaDelProveedor(
                "{\"mensaje\":\"Llama al 107.\",\"especialidad\":\"Cardiologia\",\"urgente\":true}"));

        assertTrue(sugerencia.isUrgente());
    }

    @Test
    void siElModeloNoRespondeJsonSeMuestraSuTextoSinEspecialidad() {
        SugerenciaEspecialidad sugerencia = AdaptadorIaGroq.interpretarRespuesta(
                respuestaDelProveedor("Te recomiendo Pediatria."));

        assertEquals("Te recomiendo Pediatria.", sugerencia.getMensaje());
        assertNull(sugerencia.getEspecialidad());
    }

    @Test
    void unaRespuestaIlegibleOVaciaEsUnaFallaDeIntegracion() {
        assertThrows(IntegracionExternaException.class, () -> AdaptadorIaGroq.interpretarRespuesta("{}"));
        assertThrows(IntegracionExternaException.class, () -> AdaptadorIaGroq.interpretarRespuesta("no es json"));
        assertThrows(IntegracionExternaException.class, () -> AdaptadorIaGroq.interpretarRespuesta(
                respuestaDelProveedor("{\"mensaje\":\"\",\"especialidad\":null}")));
    }
}
