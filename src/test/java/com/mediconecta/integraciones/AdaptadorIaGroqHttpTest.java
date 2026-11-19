package com.mediconecta.integraciones;

import com.mediconecta.negocio.IntegracionExternaException;

import com.sun.net.httpserver.HttpServer;

import jakarta.json.Json;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * El cliente HTTP de {@link AdaptadorIaGroq} contra un servidor HTTP local que
 * imita la API "chat completions": formato del pedido, cabecera de
 * autorizacion, errores del proveedor y timeout. No usa la red ni una clave real.
 */
class AdaptadorIaGroqHttpTest {

    private HttpServer servidor;

    private final AtomicReference<String> cuerpoRecibido = new AtomicReference<>();

    private final AtomicReference<String> autorizacionRecibida = new AtomicReference<>();

    private volatile int estado = 200;

    private volatile String respuesta;

    private volatile long demoraMilisegundos;

    @BeforeEach
    void levantar() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/v1/chat/completions", intercambio -> {
            cuerpoRecibido.set(new String(intercambio.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            autorizacionRecibida.set(intercambio.getRequestHeaders().getFirst("Authorization"));
            try {
                Thread.sleep(demoraMilisegundos);
            } catch (InterruptedException interrumpido) {
                Thread.currentThread().interrupt();
            }
            byte[] bytes = respuesta.getBytes(StandardCharsets.UTF_8);
            intercambio.getResponseHeaders().add("Content-Type", "application/json");
            intercambio.sendResponseHeaders(estado, bytes.length);
            try (OutputStream salida = intercambio.getResponseBody()) {
                salida.write(bytes);
            }
        });
        servidor.start();
        System.setProperty("mediconecta.ia.url",
                "http://127.0.0.1:" + servidor.getAddress().getPort() + "/v1/chat/completions");
        System.setProperty("mediconecta.ia.clave", "clave-de-prueba-no-real");
        System.setProperty("mediconecta.ia.modelo", "modelo-de-prueba");
    }

    @AfterEach
    void bajar() {
        servidor.stop(0);
        System.clearProperty("mediconecta.ia.url");
        System.clearProperty("mediconecta.ia.clave");
        System.clearProperty("mediconecta.ia.modelo");
        System.clearProperty("mediconecta.ia.timeoutSegundos");
    }

    private static String chatCompletion(String contenido) {
        return Json.createObjectBuilder()
                .add("choices", Json.createArrayBuilder().add(Json.createObjectBuilder()
                        .add("message", Json.createObjectBuilder().add("role", "assistant").add("content", contenido))))
                .build().toString();
    }

    private SugerenciaEspecialidad orientar() {
        return new AdaptadorIaGroq().orientar(
                List.of(new MensajeChat(MensajeChat.USUARIO, "Me duele el pecho al correr")),
                List.of("Cardiologia", "Clinica Medica"));
    }

    @Test
    void mandaElPedidoAlProveedorYLeeLaSugerencia() {
        respuesta = chatCompletion("{\"mensaje\":\"Te conviene Cardiologia.\",\"especialidad\":\"Cardiologia\",\"urgente\":false}");

        SugerenciaEspecialidad sugerencia = orientar();

        assertEquals("Cardiologia", sugerencia.getEspecialidad());
        assertEquals(AdaptadorIaGroq.ORIGEN, sugerencia.getOrigen());
        assertEquals("Bearer clave-de-prueba-no-real", autorizacionRecibida.get());
        assertTrue(cuerpoRecibido.get().contains("\"model\":\"modelo-de-prueba\""));
        assertTrue(cuerpoRecibido.get().contains("Me duele el pecho al correr"));
    }

    @Test
    void unErrorDelProveedorSeTraduceAFallaDeIntegracion() {
        estado = 503;
        respuesta = "{\"error\":{\"message\":\"sobrecargado\"}}";
        IntegracionExternaException error = assertThrows(IntegracionExternaException.class, this::orientar);
        assertTrue(error.getMessage().contains("no esta disponible"));
    }

    @Test
    void unaRespuestaIlegibleSeTraduceAFallaDeIntegracion() {
        respuesta = "{\"choices\":[]}";
        assertThrows(IntegracionExternaException.class, this::orientar);
    }

    @Test
    void siElProveedorTardaMasQueElTimeoutSeCorta() {
        System.setProperty("mediconecta.ia.timeoutSegundos", "1");
        demoraMilisegundos = 3_000;
        respuesta = chatCompletion("{\"mensaje\":\"tarde\"}");
        long inicio = System.nanoTime();
        assertThrows(IntegracionExternaException.class, this::orientar);
        assertTrue(System.nanoTime() - inicio < 2_800_000_000L, "el cliente tiene que cortar antes que el servidor");
    }

    @Test
    void sinClaveConfiguradaNoLlamaAlProveedor() {
        System.clearProperty("mediconecta.ia.clave");
        if (System.getenv("MEDICONECTA_IA_CLAVE") == null) {
            assertThrows(IntegracionExternaException.class, this::orientar);
            assertEquals(null, cuerpoRecibido.get());
        }
    }

    @Test
    void elTimeoutConfigurableQuedaEntreUnoYCincoSegundos() {
        assertEquals(5, AdaptadorIaGroq.timeoutSegundos());
        System.setProperty("mediconecta.ia.timeoutSegundos", "3");
        assertEquals(3, AdaptadorIaGroq.timeoutSegundos());
        System.setProperty("mediconecta.ia.timeoutSegundos", "60");
        assertEquals(5, AdaptadorIaGroq.timeoutSegundos());
        System.setProperty("mediconecta.ia.timeoutSegundos", "0");
        assertEquals(5, AdaptadorIaGroq.timeoutSegundos());
        System.setProperty("mediconecta.ia.timeoutSegundos", "abc");
        assertEquals(5, AdaptadorIaGroq.timeoutSegundos());
    }

    @Test
    void elPartnerRestTraduceErroresYCaidas() {
        servidor.createContext("/partner/cobertura", intercambio -> {
            intercambio.sendResponseHeaders(500, -1);
            intercambio.close();
        });
        System.setProperty("mediconecta.partner.baseUrl",
                "http://127.0.0.1:" + servidor.getAddress().getPort() + "/partner/cobertura");
        try {
            IntegracionExternaException error = assertThrows(IntegracionExternaException.class,
                    () -> new AdaptadorObraSocialPartner().validar("GALENO", "1"));
            assertTrue(error.getMessage().contains("500"));

            servidor.stop(0);
            assertThrows(IntegracionExternaException.class,
                    () -> new AdaptadorObraSocialPartner().validar("GALENO", "1"));
        } finally {
            System.clearProperty("mediconecta.partner.baseUrl");
        }
    }
}
