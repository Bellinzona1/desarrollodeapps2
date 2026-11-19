package com.mediconecta.integraciones;

/**
 * Resultado uniforme de validar la cobertura de un paciente, sin importar si
 * detras se consulto el sistema legado (SOAP) o el partner moderno (REST).
 */
public class ResultadoCobertura {

    private boolean vigente;

    private String plan;

    private double copago;

    /** De donde vino el dato: {@code LEGADO}, {@code PARTNER}, {@code NINGUNA} o {@code SIN_VERIFICAR}. */
    private String origen;

    /**
     * {@code true} si el sistema externo no respondio y el dato es de
     * contingencia (la ultima consulta exitosa guardada, o "sin verificar").
     */
    private boolean contingencia;

    /** Aclaracion para mostrar cuando el dato es de contingencia. */
    private String mensaje;

    public ResultadoCobertura() {
    }

    public ResultadoCobertura(boolean vigente, String plan, double copago, String origen) {
        this.vigente = vigente;
        this.plan = plan;
        this.copago = copago;
        this.origen = origen;
    }

    public boolean isVigente() {
        return vigente;
    }

    public void setVigente(boolean vigente) {
        this.vigente = vigente;
    }

    public String getPlan() {
        return plan;
    }

    public void setPlan(String plan) {
        this.plan = plan;
    }

    public double getCopago() {
        return copago;
    }

    public void setCopago(double copago) {
        this.copago = copago;
    }

    /** Copia de este resultado marcada como dato de contingencia. */
    public ResultadoCobertura enContingencia(String aclaracion) {
        ResultadoCobertura copia = new ResultadoCobertura(vigente, plan, copago, origen);
        copia.setContingencia(true);
        copia.setMensaje(aclaracion);
        return copia;
    }

    public boolean isContingencia() {
        return contingencia;
    }

    public void setContingencia(boolean contingencia) {
        this.contingencia = contingencia;
    }

    public String getMensaje() {
        return mensaje;
    }

    public void setMensaje(String mensaje) {
        this.mensaje = mensaje;
    }

    public String getOrigen() {
        return origen;
    }

    public void setOrigen(String origen) {
        this.origen = origen;
    }
}
