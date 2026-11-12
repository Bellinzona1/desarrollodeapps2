package com.mediconecta.integraciones;

import com.mediconecta.modelo.Paciente;
import com.mediconecta.negocio.GestionPacientes;
import com.mediconecta.negocio.Roles;

import jakarta.annotation.security.RolesAllowed;
import jakarta.ejb.Stateless;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Patron Adapter: valida la cobertura del paciente contra el sistema que
 * corresponda, con una interfaz unica para quien lo consume.
 *
 * <p>Las obras sociales de {@link #LEGADAS} se resuelven contra el sistema
 * legado por SOAP ({@link AdaptadorObraSocialLegado}); cualquier otra, contra
 * el partner moderno por REST ({@link AdaptadorObraSocialPartner}). Quien
 * llama a {@link #validarCobertura(String)} no sabe cual de los dos se uso:
 * esa es la idea del patron.</p>
 */
@Stateless
public class GestorCobertura {

    private static final Set<String> LEGADAS = Set.of("PAMI", "OSDE", "IOMA");

    /**
     * Obras sociales conocidas. La ficha del paciente guarda la cobertura con
     * el plan ("OSDE 210", "Swiss Medical SMG02"), asi que se identifica la
     * obra social por el comienzo del texto. Las mas largas van primero.
     */
    private static final List<String> CONOCIDAS =
            List.of("SWISS MEDICAL", "MEDICUS", "GALENO", "PAMI", "OSDE", "IOMA");

    @Inject
    private GestionPacientes gestionPacientes;

    @Inject
    private AdaptadorObraSocialLegado adaptadorLegado;

    @Inject
    private AdaptadorObraSocialPartner adaptadorPartner;

    @RolesAllowed({Roles.PACIENTE, Roles.RECEPCION, Roles.MEDICO, Roles.ADMIN})
    public ResultadoCobertura validarCobertura(String documento) {
        Paciente paciente = gestionPacientes.buscarPorDocumento(documento);
        gestionPacientes.exigirPropioDocumentoSiEsPaciente(paciente.getDocumento());

        String obraSocial = paciente.getCobertura();
        if (obraSocial == null || obraSocial.isBlank()) {
            return new ResultadoCobertura(false, null, 0, "NINGUNA");
        }

        String nombre = obraSocialDe(obraSocial);
        ValidadorCobertura adaptador = esLegada(nombre) ? adaptadorLegado : adaptadorPartner;
        return adaptador.validar(nombre, paciente.getDocumento());
    }

    /** "OSDE 210" -> "OSDE"; "Swiss Medical SMG02" -> "SWISS MEDICAL"; otra -> tal cual, en mayusculas. */
    static String obraSocialDe(String cobertura) {
        String texto = cobertura.trim().toUpperCase(Locale.ROOT);
        for (String conocida : CONOCIDAS) {
            if (texto.equals(conocida) || texto.startsWith(conocida + " ")) {
                return conocida;
            }
        }
        return texto;
    }

    static boolean esLegada(String obraSocial) {
        return LEGADAS.contains(obraSocial);
    }
}
