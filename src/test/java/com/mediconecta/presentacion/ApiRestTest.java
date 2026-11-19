package com.mediconecta.presentacion;

import com.mediconecta.negocio.Escenario;
import com.mediconecta.negocio.Inyeccion;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;

import org.jboss.resteasy.mock.MockDispatcherFactory;
import org.jboss.resteasy.mock.MockHttpRequest;
import org.jboss.resteasy.mock.MockHttpResponse;
import org.jboss.resteasy.spi.Dispatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * La API REST real (recursos, mappers y filtros) sobre el dispatcher en
 * memoria de RESTEasy, con el negocio cableado sobre los repositorios en
 * memoria. Verifica el contrato HTTP de la Entrega 3: Problem Details,
 * ETag / pedidos condicionales e Idempotency-Key.
 */
class ApiRestTest {

    private Dispatcher dispatcher;

    private Escenario e;

    @BeforeEach
    void preparar() {
        e = new Escenario();
        dispatcher = MockDispatcherFactory.createDispatcher();

        RecursoTurnos turnos = new RecursoTurnos();
        Inyeccion.campo(turnos, "gestionTurnos", e.gestionTurnos);
        Inyeccion.campo(turnos, "fachadaReservas", e.fachada);
        dispatcher.getRegistry().addSingletonResource(turnos);

        RecursoPacientes pacientes = new RecursoPacientes();
        Inyeccion.campo(pacientes, "gestionPacientes", e.gestionPacientes);
        dispatcher.getRegistry().addSingletonResource(pacientes);

        dispatcher.getRegistry().addSingletonResource(new RecursoSalud());

        for (Class<?> proveedor : new Class<?>[]{ManejadorAccesoDenegado.class, ManejadorPropiedadAjena.class,
                ManejadorCredencialesInvalidas.class, ManejadorIntegracionExterna.class, ManejadorNoEncontrado.class,
                ManejadorReglaNegocio.class, ManejadorTransicionInvalida.class, ManejadorDemasiadosIntentos.class,
                ManejadorIdempotencia.class, ManejadorErrorInesperado.class, FiltroCacheApi.class}) {
            dispatcher.getProviderFactory().registerProvider(proveedor);
        }
    }

    // ---------------------------------------------------------------- ayudas

    private MockHttpResponse llamar(String metodo, String uri, String cuerpo, Map<String, String> cabeceras)
            throws URISyntaxException {
        MockHttpRequest pedido = MockHttpRequest.create(metodo, uri).accept(MediaType.APPLICATION_JSON);
        if (cuerpo != null) {
            pedido.contentType(MediaType.APPLICATION_JSON_TYPE).content(cuerpo.getBytes(StandardCharsets.UTF_8));
        }
        cabeceras.forEach(pedido::header);
        MockHttpResponse respuesta = new MockHttpResponse();
        dispatcher.invoke(pedido, respuesta);
        return respuesta;
    }

    private MockHttpResponse llamar(String metodo, String uri) throws URISyntaxException {
        return llamar(metodo, uri, null, Map.of());
    }

    private static String texto(MockHttpResponse respuesta) {
        return new String(respuesta.getOutput(), StandardCharsets.UTF_8);
    }

    private static JsonObject json(MockHttpResponse respuesta) {
        return Json.createReader(new StringReader(texto(respuesta))).readObject();
    }

    private static String cabecera(MockHttpResponse respuesta, String nombre) {
        Object valor = respuesta.getOutputHeaders().getFirst(nombre);
        return valor == null ? null : valor.toString();
    }

    private static void esProblema(MockHttpResponse respuesta, int estado, String tipo) {
        assertEquals(estado, respuesta.getStatus(), texto(respuesta));
        assertTrue(cabecera(respuesta, HttpHeaders.CONTENT_TYPE).startsWith(Problemas.TIPO_CONTENIDO));
        JsonObject problema = json(respuesta);
        assertEquals(Problemas.tipo(tipo), problema.getString("type"));
        assertEquals(estado, problema.getInt("status"));
        assertFalse(problema.getString("title").isBlank());
        assertEquals(problema.getString("detail"), problema.getString("error"));
        assertEquals(estado, problema.getInt("codigo"));
        assertNotNull(problema.getString("instance"));
    }

    private String pedidoDeTurno(int hora) {
        return Json.createObjectBuilder()
                .add("documentoPaciente", "30111222")
                .add("profesionalId", e.laura.getId())
                .add("especialidad", "Cardiologia")
                .add("fechaHora", Escenario.proximoHabil(hora, 0))
                .add("motivo", "Control")
                .build().toString();
    }

    private long crearTurno() throws URISyntaxException {
        MockHttpResponse creado = llamar("POST", "/turnos", pedidoDeTurno(10), Map.of());
        assertEquals(201, creado.getStatus(), texto(creado));
        return json(creado).getJsonNumber("id").longValue();
    }

    // ------------------------------------------------------- Idempotency-Key

    @Test
    void elAltaDevuelve201ConLocationYETag() throws Exception {
        MockHttpResponse creado = llamar("POST", "/turnos", pedidoDeTurno(10), Map.of());
        assertEquals(201, creado.getStatus());
        assertTrue(cabecera(creado, HttpHeaders.LOCATION).endsWith("/turnos/" + json(creado).getInt("id")));
        assertNotNull(cabecera(creado, HttpHeaders.ETAG));
        assertNull(cabecera(creado, RecursoTurnos.CABECERA_REPETIDA));
    }

    @Test
    void unReintentoConLaMismaIdempotencyKeyDevuelveElMismoTurno() throws Exception {
        Map<String, String> clave = Map.of(RecursoTurnos.CABECERA_IDEMPOTENCIA, "8c2b1f4e-0d1a-4a77-9f3e-1b2c3d4e5f60");
        MockHttpResponse primero = llamar("POST", "/turnos", pedidoDeTurno(10), clave);
        MockHttpResponse reintento = llamar("POST", "/turnos", pedidoDeTurno(10), clave);

        assertEquals(201, primero.getStatus());
        assertEquals(201, reintento.getStatus());
        assertEquals("true", cabecera(reintento, RecursoTurnos.CABECERA_REPETIDA));
        assertEquals(json(primero).getInt("id"), json(reintento).getInt("id"));
        assertEquals(cabecera(primero, HttpHeaders.LOCATION), cabecera(reintento, HttpHeaders.LOCATION));
        assertEquals(1, e.repoTurnos.listarTodos().size());
        assertEquals(1, e.notificador.enviados.size());
    }

    @Test
    void laMismaClaveConOtroCuerpoDa422() throws Exception {
        Map<String, String> clave = Map.of(RecursoTurnos.CABECERA_IDEMPOTENCIA, "clave-1");
        llamar("POST", "/turnos", pedidoDeTurno(10), clave);
        esProblema(llamar("POST", "/turnos", pedidoDeTurno(11), clave), 422, "idempotency-key-reutilizada");
    }

    @Test
    void unaClaveInvalidaDa400() throws Exception {
        esProblema(llamar("POST", "/turnos", pedidoDeTurno(10),
                Map.of(RecursoTurnos.CABECERA_IDEMPOTENCIA, "no valida")), 400, "regla-de-negocio");
    }

    // ------------------------------------------------- ETag y condicionales

    @Test
    void getConIfNoneMatchVigenteDa304() throws Exception {
        long id = crearTurno();
        MockHttpResponse primero = llamar("GET", "/turnos/" + id);
        assertEquals(200, primero.getStatus());
        String etag = cabecera(primero, HttpHeaders.ETAG);
        assertNotNull(etag);
        assertTrue(cabecera(primero, HttpHeaders.CACHE_CONTROL).contains("no-cache"));

        MockHttpResponse condicional = llamar("GET", "/turnos/" + id, null, Map.of(HttpHeaders.IF_NONE_MATCH, etag));
        assertEquals(304, condicional.getStatus());
        assertEquals("", texto(condicional));

        MockHttpResponse otraVersion = llamar("GET", "/turnos/" + id, null, Map.of(HttpHeaders.IF_NONE_MATCH, "\"vieja\""));
        assertEquals(200, otraVersion.getStatus());
    }

    @Test
    void cancelarConIfMatchViejoDa412YNoCambiaElTurno() throws Exception {
        long id = crearTurno();
        String etag = cabecera(llamar("GET", "/turnos/" + id), HttpHeaders.ETAG);

        MockHttpResponse cancelado = llamar("PUT", "/turnos/" + id + "/cancelar", "", Map.of(HttpHeaders.IF_MATCH, etag));
        assertEquals(200, cancelado.getStatus());
        assertEquals("CANCELADO", json(cancelado).getString("estado"));
        String nuevaEtag = cabecera(cancelado, HttpHeaders.ETAG);
        assertNotEquals(etag, nuevaEtag);

        esProblema(llamar("PUT", "/turnos/" + id + "/archivar", "", Map.of(HttpHeaders.IF_MATCH, etag)),
                412, "precondicion-fallida");
        assertFalse(e.gestionTurnos.obtenerTurno(id).isArchivado());

        assertEquals(200, llamar("PUT", "/turnos/" + id + "/archivar", "",
                Map.of(HttpHeaders.IF_MATCH, nuevaEtag)).getStatus());
    }

    @Test
    void lasTransicionesSinIfMatchSiguenFuncionando() throws Exception {
        long id = crearTurno();
        e.como("medico", "MEDICO");
        assertEquals(200, llamar("PUT", "/turnos/" + id + "/atender", "", Map.of()).getStatus());
        esProblema(llamar("PUT", "/turnos/" + id + "/cancelar", "", Map.of()), 409, "transicion-invalida");
    }

    @Test
    void laFichaDelPacienteTieneETagYAceptaIfMatch() throws Exception {
        MockHttpResponse ficha = llamar("GET", "/pacientes/30111222");
        String etag = cabecera(ficha, HttpHeaders.ETAG);
        assertNotNull(etag);
        assertEquals(304, llamar("GET", "/pacientes/30111222", null, Map.of(HttpHeaders.IF_NONE_MATCH, etag)).getStatus());

        String cambios = "{\"nombre\":\"Ana Gomez\",\"telefono\":\"11-0000-0000\",\"cobertura\":\"OSDE 210\",\"activo\":true}";
        MockHttpResponse actualizada = llamar("PUT", "/pacientes/30111222", cambios, Map.of(HttpHeaders.IF_MATCH, etag));
        assertEquals(200, actualizada.getStatus(), texto(actualizada));
        esProblema(llamar("PUT", "/pacientes/30111222", cambios, Map.of(HttpHeaders.IF_MATCH, etag)),
                412, "precondicion-fallida");
        assertEquals(200, llamar("PUT", "/pacientes/30111222", cambios, Map.of()).getStatus());
    }

    @Test
    void elAltaDePacienteDevuelve201ConLocationYRechazaDocumentosInvalidos() throws Exception {
        MockHttpResponse creado = llamar("POST", "/pacientes", "{\"nombre\":\"Eva\",\"documento\":\"30999888\"}", Map.of());
        assertEquals(201, creado.getStatus(), texto(creado));
        assertTrue(cabecera(creado, HttpHeaders.LOCATION).endsWith("/pacientes/30999888"));

        esProblema(llamar("POST", "/pacientes", "{\"nombre\":\"Eva\",\"documento\":\"{zap}123\"}", Map.of()),
                400, "regla-de-negocio");
        assertEquals(3, e.repoPacientes.listarTodos().size());
    }

    // ------------------------------------------------------ Problem Details

    @Test
    void lasReglasDeNegocioDan400() throws Exception {
        String sinProfesional = "{\"documentoPaciente\":\"30111222\",\"fechaHora\":\"" + Escenario.proximoHabil(10, 0) + "\"}";
        esProblema(llamar("POST", "/turnos", sinProfesional, Map.of()), 400, "regla-de-negocio");
    }

    @Test
    void unRecursoDeNegocioInexistenteDa404() throws Exception {
        esProblema(llamar("GET", "/turnos/999"), 404, "no-encontrado");
    }

    @Test
    void unPacienteQuePideUnTurnoAjenoRecibe403() throws Exception {
        long id = crearTurno();
        e.como("otro-paciente", "PACIENTE");
        esProblema(llamar("GET", "/turnos/" + id), 403, "recurso-ajeno");
        esProblema(llamar("GET", "/pacientes/30111222"), 403, "recurso-ajeno");
    }

    @Test
    void losErroresDelPropioJaxRsTambienSonProblemDetails() throws Exception {
        esProblema(llamar("GET", "/no-existe"), 404, "http-404");
        MockHttpResponse metodo = llamar("DELETE", "/turnos/1");
        esProblema(metodo, 405, "http-405");
        esProblema(llamar("POST", "/turnos", "{esto no es json", Map.of()), 400, "cuerpo-invalido");
    }

    @Test
    void elJsonDelProblemaNoExponeDetallesInternos() throws Exception {
        MockHttpResponse respuesta = llamar("POST", "/turnos", "{esto no es json", Map.of());
        String texto = texto(respuesta);
        assertFalse(texto.contains("Exception"), texto);
        assertFalse(texto.contains("at org."), texto);
    }

    @Test
    void laApiNoSeCacheaSalvoLoQueTieneETag() throws Exception {
        crearTurno();
        assertEquals("no-store", cabecera(llamar("GET", "/turnos"), HttpHeaders.CACHE_CONTROL));
        MockHttpRequest salud = MockHttpRequest.get("/salud");
        MockHttpResponse respuesta = new MockHttpResponse();
        dispatcher.invoke(salud, respuesta);
        assertEquals(200, respuesta.getStatus());
        assertTrue(texto(respuesta).contains("Hola desde el contenedor"));
    }
}
