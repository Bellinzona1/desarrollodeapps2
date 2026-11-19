package com.mediconecta.presentacion;

import jakarta.annotation.security.DenyAll;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ejb.Schedule;
import jakarta.ejb.Stateful;
import jakarta.ejb.Stateless;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Control de BFLA (autorizacion a nivel de funcion): todo metodo publico de un
 * EJB de sesion tiene que declarar quien puede invocarlo. Si alguien agrega un
 * metodo de negocio y se olvida del {@code @RolesAllowed}, este test falla.
 *
 * <p>Las unicas excepciones son los EJB que no son puertas de entrada: los
 * adapters de integracion (solo los invocan otros EJB que ya exigieron rol),
 * el productor JMS, el timer de limpieza y el sistema legado simulado (que
 * representa a un sistema externo publico).</p>
 */
class AutorizacionDeclaradaTest {

    private static final Set<String> SIN_ROL_A_PROPOSITO = Set.of(
            "com.mediconecta.integraciones.AdaptadorIaGroq",
            "com.mediconecta.integraciones.AdaptadorObraSocialLegado",
            "com.mediconecta.integraciones.AdaptadorObraSocialPartner",
            "com.mediconecta.integraciones.simulacion.ServicioObraSocialLegadoImpl",
            "com.mediconecta.mensajeria.NotificadorTurnos",
            "com.mediconecta.negocio.LimpiezaClavesIdempotencia");

    private static List<Class<?>> ejbsDeSesion() throws IOException, URISyntaxException, ClassNotFoundException {
        Path raiz = Path.of(RecursoTurnos.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        List<Class<?>> ejbs = new ArrayList<>();
        try (Stream<Path> archivos = Files.walk(raiz)) {
            for (Path archivo : archivos.filter(p -> p.toString().endsWith(".class")).toList()) {
                String nombre = raiz.relativize(archivo).toString()
                        .replace(java.io.File.separatorChar, '.').replaceAll("\\.class$", "");
                if (nombre.contains("$")) {
                    continue;
                }
                Class<?> clase = Class.forName(nombre, false, AutorizacionDeclaradaTest.class.getClassLoader());
                if (clase.isAnnotationPresent(Stateless.class) || clase.isAnnotationPresent(Stateful.class)) {
                    ejbs.add(clase);
                }
            }
        }
        return ejbs;
    }

    private static boolean declaraAcceso(Method metodo) {
        return metodo.isAnnotationPresent(RolesAllowed.class) || metodo.isAnnotationPresent(PermitAll.class)
                || metodo.isAnnotationPresent(DenyAll.class) || metodo.isAnnotationPresent(Schedule.class);
    }

    @Test
    void todoMetodoPublicoDeUnEjbDeclaraQuienPuedeInvocarlo() throws Exception {
        List<Class<?>> ejbs = ejbsDeSesion();
        assertTrue(ejbs.size() >= 10, "no se encontraron los EJB: " + ejbs);

        List<String> sinDeclarar = new ArrayList<>();
        for (Class<?> ejb : ejbs) {
            if (SIN_ROL_A_PROPOSITO.contains(ejb.getName()) || ejb.isAnnotationPresent(RolesAllowed.class)) {
                continue;
            }
            for (Method metodo : ejb.getDeclaredMethods()) {
                if (Modifier.isPublic(metodo.getModifiers()) && !Modifier.isStatic(metodo.getModifiers())
                        && !metodo.isSynthetic() && !declaraAcceso(metodo)) {
                    sinDeclarar.add(ejb.getSimpleName() + "." + metodo.getName());
                }
            }
        }
        assertEquals(List.of(), sinDeclarar, "metodos publicos de EJB sin @RolesAllowed/@PermitAll");
    }

    @Test
    void lasOperacionesSensiblesQuedanRestringidas() throws Exception {
        assertEquals(List.of("ADMIN"), roles("com.mediconecta.negocio.GestionProfesionales", "registrarProfesional"));
        assertEquals(List.of("RECEPCION", "ADMIN"), roles("com.mediconecta.negocio.GestionTurnos", "archivarTurno"));
        assertEquals(List.of("MEDICO", "ADMIN"), roles("com.mediconecta.negocio.GestionTurnos", "atenderTurno"));
        assertEquals(List.of("RECEPCION", "MEDICO", "ADMIN"), roles("com.mediconecta.negocio.GestionTurnos", "listarTurnos"));
        RolesAllowed diagnostico = Class.forName("com.mediconecta.mensajeria.DiagnosticoMensajeria")
                .getAnnotation(RolesAllowed.class);
        assertEquals(List.of("ADMIN"), List.of(diagnostico.value()));
    }

    private static List<String> roles(String clase, String metodo) throws ClassNotFoundException {
        for (Method m : Class.forName(clase).getDeclaredMethods()) {
            if (m.getName().equals(metodo)) {
                return List.of(m.getAnnotation(RolesAllowed.class).value());
            }
        }
        throw new AssertionError("No existe " + clase + "." + metodo);
    }
}
