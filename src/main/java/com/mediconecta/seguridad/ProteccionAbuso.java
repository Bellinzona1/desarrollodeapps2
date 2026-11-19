package com.mediconecta.seguridad;

import jakarta.enterprise.context.ApplicationScoped;

import java.time.Clock;
import java.time.Duration;
import java.util.Locale;

/**
 * Limites contra fuerza bruta y abuso, compartidos por toda la aplicacion.
 *
 * <ul>
 *   <li><b>Login por cuenta:</b> {@value #FALLOS_POR_CUENTA} intentos fallidos
 *       en 15 minutos bloquean esa cuenta hasta que el mas viejo salga de la
 *       ventana. Frena el ataque de diccionario contra un usuario.</li>
 *   <li><b>Login por origen (IP):</b> {@value #FALLOS_POR_ORIGEN} fallidos en
 *       15 minutos, sumando todas las cuentas. Frena el "password spraying"
 *       (una clave comun probada contra muchos usuarios).</li>
 *   <li><b>Asistente de IA por usuario:</b> {@value #ORIENTACIONES_POR_MINUTO}
 *       pedidos por minuto. Cada pedido consume cuota paga del proveedor.</li>
 * </ul>
 *
 * <p>Un login correcto limpia el contador de la cuenta (no el del origen). El
 * bloqueo por cuenta es temporal a proposito: un bloqueo permanente le
 * permitiria a un atacante dejar sin acceso a cualquier usuario.</p>
 */
@ApplicationScoped
public class ProteccionAbuso {

    public static final int FALLOS_POR_CUENTA = 5;

    /**
     * Alto a proposito: varios usuarios legitimos pueden compartir una IP (NAT
     * de la clinica) y las pruebas automaticas fallan logins a proposito.
     */
    public static final int FALLOS_POR_ORIGEN = 50;

    public static final int ORIENTACIONES_POR_MINUTO = 10;

    private static final Duration VENTANA_LOGIN = Duration.ofMinutes(15);

    private final LimitadorDeIntentos fallosPorCuenta;

    private final LimitadorDeIntentos fallosPorOrigen;

    private final LimitadorDeIntentos orientaciones;

    public ProteccionAbuso() {
        this(Clock.systemUTC());
    }

    /** Constructor para los tests: permite adelantar el reloj. */
    public ProteccionAbuso(Clock reloj) {
        this.fallosPorCuenta = new LimitadorDeIntentos(FALLOS_POR_CUENTA, VENTANA_LOGIN, reloj);
        this.fallosPorOrigen = new LimitadorDeIntentos(FALLOS_POR_ORIGEN, VENTANA_LOGIN, reloj);
        this.orientaciones = new LimitadorDeIntentos(ORIENTACIONES_POR_MINUTO, Duration.ofMinutes(1), reloj);
    }

    /** Segundos de espera antes de poder intentar el login, o 0. */
    public long bloqueoDeLogin(String usuario, String origen) {
        long porCuenta = fallosPorCuenta.segundosDeBloqueo(cuenta(usuario));
        long porOrigen = origen == null ? 0 : fallosPorOrigen.segundosDeBloqueo(origen);
        return Math.max(porCuenta, porOrigen);
    }

    public void loginFallido(String usuario, String origen) {
        fallosPorCuenta.registrar(cuenta(usuario));
        if (origen != null) {
            fallosPorOrigen.registrar(origen);
        }
    }

    public void loginCorrecto(String usuario) {
        fallosPorCuenta.olvidar(cuenta(usuario));
    }

    /** Cuenta un pedido al asistente de IA; devuelve la espera si se paso del limite, o 0. */
    public long consumirOrientacion(String usuario) {
        return orientaciones.consumir(cuenta(usuario));
    }

    private static String cuenta(String usuario) {
        return usuario == null ? "" : usuario.trim().toLowerCase(Locale.ROOT);
    }
}
