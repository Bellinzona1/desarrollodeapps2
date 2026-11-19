package com.mediconecta.negocio;

import jakarta.ejb.SessionContext;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.security.Principal;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Ayudas para probar los EJB como objetos Java comunes, sin contenedor.
 *
 * <p>Las dependencias se completan con las implementaciones en memoria de los
 * repositorios: es el mismo patron DAO que habilita el "plan B" sin base de
 * datos, usado aca para que los tests no necesiten PostgreSQL ni WildFly.</p>
 *
 * <p>Es publica para que la usen los tests de los otros paquetes
 * (integraciones, mensajeria, presentacion).</p>
 */
public final class Inyeccion {

    private Inyeccion() {
    }

    /**
     * Asigna un campo privado, como haria {@code @Inject} o {@code @Resource}.
     * Busca el campo tambien en las superclases (sirve para dobles de prueba
     * que extienden al EJB real).
     */
    public static void campo(Object destino, String nombre, Object valor) {
        Class<?> tipo = destino.getClass();
        while (tipo != null) {
            try {
                Field campo = tipo.getDeclaredField(nombre);
                campo.setAccessible(true);
                campo.set(destino, valor);
                return;
            } catch (NoSuchFieldException e) {
                tipo = tipo.getSuperclass();
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("No se pudo inyectar " + nombre, e);
            }
        }
        throw new IllegalStateException("No existe el campo " + nombre + " en " + destino.getClass());
    }

    /**
     * Un {@link SessionContext} que solo responde quien es el llamador y que
     * roles tiene: lo unico que usan los componentes para el ownership.
     */
    public static SessionContext llamador(String usuario, String... roles) {
        Set<String> conjunto = Set.of(roles);
        return llamadorVariable(() -> usuario, () -> conjunto);
    }

    /** Igual que {@link #llamador}, pero el usuario y los roles se consultan en cada llamada. */
    public static SessionContext llamadorVariable(Supplier<String> usuario, Supplier<Set<String>> roles) {
        return (SessionContext) Proxy.newProxyInstance(
                Inyeccion.class.getClassLoader(),
                new Class<?>[]{SessionContext.class},
                (proxy, metodo, args) -> switch (metodo.getName()) {
                    case "getCallerPrincipal" -> (Principal) usuario::get;
                    case "isCallerInRole" -> roles.get().contains((String) args[0]);
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "toString" -> "SessionContext de prueba";
                    default -> throw new UnsupportedOperationException(metodo.getName());
                });
    }
}
