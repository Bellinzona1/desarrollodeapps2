package com.mediconecta.integraciones;

import com.mediconecta.integraciones.simulacion.RecursoPartnerCobertura;
import com.mediconecta.integraciones.simulacion.RespuestaPartner;
import com.mediconecta.integraciones.simulacion.ServicioObraSocialLegadoImpl;
import com.mediconecta.modelo.Paciente;
import com.mediconecta.negocio.AccesoDenegadoException;
import com.mediconecta.negocio.Escenario;
import com.mediconecta.negocio.Inyeccion;
import com.mediconecta.negocio.IntegracionExternaException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link GestorCobertura} de punta a punta con adapters de prueba: eleccion del
 * sistema (Adapter), fallback al ultimo dato bueno y resultado "sin verificar".
 */
class CoberturaResilienteTest {

    private Escenario e;

    private GestorCobertura gestor;

    private ExecutorService ejecutor;

    private final AtomicBoolean legadoCaido = new AtomicBoolean();

    private final AtomicInteger llamadasLegado = new AtomicInteger();

    private final AtomicInteger llamadasPartner = new AtomicInteger();

    /** El "sistema legado": responde con la implementacion simulada real, o falla. */
    private final class LegadoDePrueba extends AdaptadorObraSocialLegado {
        @Override
        public ResultadoCobertura validar(String obraSocial, String numeroAfiliado) {
            llamadasLegado.incrementAndGet();
            if (legadoCaido.get()) {
                throw new IntegracionExternaException("El sistema legado de obras sociales no respondio.");
            }
            ServicioObraSocialLegadoImpl sistema = new ServicioObraSocialLegadoImpl();
            return new ResultadoCobertura(sistema.consultarVigencia(obraSocial, numeroAfiliado),
                    sistema.consultarPlan(obraSocial, numeroAfiliado), 0, "LEGADO");
        }
    }

    /** El "partner": responde con el recurso simulado real. */
    private final class PartnerDePrueba extends AdaptadorObraSocialPartner {
        @Override
        public ResultadoCobertura validar(String obraSocial, String numeroAfiliado) {
            llamadasPartner.incrementAndGet();
            RespuestaPartner respuesta = new RecursoPartnerCobertura().consultar(obraSocial, numeroAfiliado);
            return new ResultadoCobertura(respuesta.isVigente(), respuesta.getPlan(), respuesta.getCopago(), "PARTNER");
        }
    }

    @BeforeEach
    void preparar() {
        e = new Escenario();
        ejecutor = Executors.newCachedThreadPool();
        gestor = new GestorCobertura();
        Inyeccion.campo(gestor, "gestionPacientes", e.gestionPacientes);
        Inyeccion.campo(gestor, "adaptadorLegado", new LegadoDePrueba());
        Inyeccion.campo(gestor, "adaptadorPartner", new PartnerDePrueba());
        Inyeccion.campo(gestor, "proteccion", new ProteccionIntegracion(Clock.systemUTC(), ejecutor, Duration.ofSeconds(2)));
        Inyeccion.campo(gestor, "memoria", new MemoriaCobertura());
    }

    @AfterEach
    void cerrar() {
        ejecutor.shutdownNow();
    }

    @Test
    void osdeSeConsultaPorSoapYSwissMedicalPorRest() {
        ResultadoCobertura ana = gestor.validarCobertura("30111222");
        assertEquals("LEGADO", ana.getOrigen());
        assertTrue(ana.isVigente());
        assertEquals("OSDE 210", ana.getPlan());
        assertFalse(ana.isContingencia());

        ResultadoCobertura bruno = gestor.validarCobertura("28999888");
        assertEquals("PARTNER", bruno.getOrigen());
        assertEquals("SMG20", bruno.getPlan());
        assertEquals(3500.0, bruno.getCopago());
        assertEquals(1, llamadasLegado.get());
        assertEquals(1, llamadasPartner.get());
    }

    @Test
    void sinCoberturaCargadaNoSeConsultaANingunSistema() {
        e.bruno.setCobertura(" ");
        assertEquals("NINGUNA", gestor.validarCobertura("28999888").getOrigen());
        e.bruno.setCobertura(null);
        assertEquals("NINGUNA", gestor.validarCobertura("28999888").getOrigen());
        assertEquals(0, llamadasPartner.get());
    }

    @Test
    void siElLegadoSeCaeDevuelveElUltimoDatoBuenoMarcadoComoContingencia() {
        gestor.validarCobertura("30111222");
        legadoCaido.set(true);

        ResultadoCobertura contingencia = gestor.validarCobertura("30111222");
        assertTrue(contingencia.isContingencia());
        assertTrue(contingencia.isVigente());
        assertEquals("OSDE 210", contingencia.getPlan());
        assertTrue(contingencia.getMensaje().contains("ultima consulta exitosa"));
    }

    @Test
    void siElLegadoSeCaeSinDatoPrevioInformaQueNoSePudoVerificar() {
        legadoCaido.set(true);
        ResultadoCobertura resultado = gestor.validarCobertura("30111222");
        assertEquals("SIN_VERIFICAR", resultado.getOrigen());
        assertTrue(resultado.isContingencia());
        assertFalse(resultado.isVigente());
        assertNull(resultado.getPlan());
        assertTrue(resultado.getMensaje().contains("OSDE"));
    }

    @Test
    void conElCircuitoAbiertoYaNoSeLlamaAlLegado() {
        legadoCaido.set(true);
        for (int i = 0; i < 5; i++) {
            gestor.validarCobertura("30111222");
        }
        assertEquals(ProteccionIntegracion.FALLOS_PARA_ABRIR, llamadasLegado.get());
    }

    @Test
    void unPacienteSoloConsultaSuPropiaCobertura() {
        e.comoPaciente();
        assertEquals("LEGADO", gestor.validarCobertura("30111222").getOrigen());
        assertThrows(AccesoDenegadoException.class, () -> gestor.validarCobertura("28999888"));
    }

    @Test
    void laMemoriaVenceALas24Horas() {
        MutableClock reloj = new MutableClock();
        MemoriaCobertura memoria = new MemoriaCobertura(reloj);
        memoria.recordar("k", new ResultadoCobertura(true, "Plan", 0, "LEGADO"));
        assertTrue(memoria.ultimo("k").isPresent());
        reloj.adelantar(Duration.ofHours(MemoriaCobertura.HORAS_VIGENCIA + 1));
        assertTrue(memoria.ultimo("k").isEmpty());
        assertTrue(memoria.ultimo("otra").isEmpty());
    }

    @Test
    void losSistemasSimuladosRespondenSegunSuTabla() {
        ServicioObraSocialLegadoImpl legado = new ServicioObraSocialLegadoImpl();
        assertTrue(legado.consultarVigencia(" pami ", "123"));
        assertFalse(legado.consultarVigencia("PAMI", " "));
        assertFalse(legado.consultarVigencia("PAMI", null));
        assertFalse(legado.consultarVigencia("GALENO", "123"));
        assertFalse(legado.consultarVigencia(null, "123"));
        assertEquals("Plan no identificado", legado.consultarPlan(null, "1"));
        assertEquals("IOMA plan unico", legado.consultarPlan("ioma", "1"));

        RecursoPartnerCobertura partner = new RecursoPartnerCobertura();
        assertTrue(partner.consultar("galeno", "1").isVigente());
        assertFalse(partner.consultar("GALENO", " ").isVigente());
        assertFalse(partner.consultar("GALENO", null).isVigente());
        assertFalse(partner.consultar(null, "1").isVigente());
        assertNull(partner.consultar("OSDE", "1").getPlan());
        assertEquals(0, partner.consultar("OSDE", "1").getCopago());
    }

    @Test
    void elAdapterSoapFijaLosTimeoutsEnAmbasVariantesDePropiedad() {
        Map<String, Object> contexto = new HashMap<>();
        AdaptadorObraSocialLegado.configurarTimeouts(contexto);
        assertEquals(AdaptadorObraSocialLegado.PROPIEDADES_TIMEOUT.length, contexto.size());
        contexto.values().forEach(valor -> assertEquals(5_000, valor));
    }

    @Test
    void elPacienteDeLaFichaNoSeModifica() {
        Paciente antes = e.gestionPacientes.buscarPorDocumento("30111222");
        gestor.validarCobertura("30111222");
        assertEquals("OSDE 210", antes.getCobertura());
    }

    /** Reloj que se adelanta a mano. */
    static final class MutableClock extends Clock {
        private java.time.Instant ahora = java.time.Instant.parse("2026-11-20T10:00:00Z");

        void adelantar(Duration tiempo) {
            ahora = ahora.plus(tiempo);
        }

        @Override
        public java.time.ZoneId getZone() {
            return java.time.ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zona) {
            return this;
        }

        @Override
        public java.time.Instant instant() {
            return ahora;
        }
    }
}
