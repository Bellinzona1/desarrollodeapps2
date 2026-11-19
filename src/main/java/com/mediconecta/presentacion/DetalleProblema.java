package com.mediconecta.presentacion;

/**
 * Cuerpo de error de la API con el formato <b>Problem Details</b> (RFC 9457),
 * tipo de contenido {@code application/problem+json}.
 *
 * <pre>
 * {"type":"urn:mediconecta:problema:regla-de-negocio","title":"Bad Request","status":400,
 *  "detail":"No se puede agendar un turno en el pasado.","instance":"/mediconecta/api/turnos",
 *  "error":"No se puede agendar un turno en el pasado.","codigo":400}
 * </pre>
 *
 * <p>{@code error} y {@code codigo} son <i>miembros de extension</i> (la RFC los
 * permite): repiten {@code detail} y {@code status} para que los clientes de
 * las entregas anteriores, que leian {@code {"error","codigo"}}, sigan
 * funcionando sin cambios.</p>
 */
public class DetalleProblema {

    private String type;

    private String title;

    private int status;

    private String detail;

    private String instance;

    public DetalleProblema() {
    }

    public DetalleProblema(String type, String title, int status, String detail, String instance) {
        this.type = type;
        this.title = title;
        this.status = status;
        this.detail = detail;
        this.instance = instance;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public String getInstance() {
        return instance;
    }

    public void setInstance(String instance) {
        this.instance = instance;
    }

    /** Extension de compatibilidad: mismo valor que {@code detail}. */
    public String getError() {
        return detail;
    }

    /** Extension de compatibilidad: mismo valor que {@code status}. */
    public int getCodigo() {
        return status;
    }
}
