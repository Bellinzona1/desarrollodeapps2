package com.mediconecta.integraciones;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Patron Adapter: eleccion del sistema externo segun la obra social. */
class GestorCoberturaTest {

    @Test
    void reconoceLaObraSocialAunqueLaCoberturaIncluyaElPlan() {
        assertEquals("OSDE", GestorCobertura.obraSocialDe("OSDE 210"));
        assertEquals("SWISS MEDICAL", GestorCobertura.obraSocialDe("Swiss Medical SMG02"));
        assertEquals("GALENO", GestorCobertura.obraSocialDe("  galeno azul "));
        assertEquals("PAMI", GestorCobertura.obraSocialDe("PAMI"));
    }

    @Test
    void unaObraSocialDesconocidaQuedaTalCual() {
        assertEquals("OSPRERA", GestorCobertura.obraSocialDe("Osprera"));
        assertEquals("OSDEX", GestorCobertura.obraSocialDe("OSDEX"));
    }

    @Test
    void lasLegadasVanPorSoapYElRestoPorElPartner() {
        assertTrue(GestorCobertura.esLegada("OSDE"));
        assertTrue(GestorCobertura.esLegada("PAMI"));
        assertTrue(GestorCobertura.esLegada("IOMA"));
        assertFalse(GestorCobertura.esLegada("SWISS MEDICAL"));
        assertFalse(GestorCobertura.esLegada("OSPRERA"));
    }
}
