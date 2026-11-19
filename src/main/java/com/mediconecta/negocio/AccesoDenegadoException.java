package com.mediconecta.negocio;

/**
 * El usuario esta autenticado y su rol tiene permiso para la operacion, pero
 * no sobre <b>ese</b> recurso: un PACIENTE pidiendo el turno de otro, un MEDICO
 * tocando la agenda de otro profesional (BOLA / IDOR).
 *
 * <p>La presentacion la traduce a {@code 403 Forbidden}. Extiende
 * {@link ReglaNegocioException} para conservar su semantica transaccional
 * ({@code rollback = false}, heredada por {@code @ApplicationException}) y para
 * que el codigo que ya la atrapaba como regla de negocio siga funcionando.</p>
 */
public class AccesoDenegadoException extends ReglaNegocioException {

    public AccesoDenegadoException(String mensaje) {
        super(mensaje);
    }
}
