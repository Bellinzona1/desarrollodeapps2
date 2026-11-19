package com.mediconecta.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

/**
 * Una {@code Idempotency-Key} ya usada para reservar un turno.
 *
 * <p>Guarda quien la uso, la huella (SHA-256) del pedido y el turno que se
 * creo. Si el cliente reintenta el mismo pedido con la misma clave, se le
 * devuelve ese turno en lugar de crear otro. La restriccion unica
 * {@code (usuario, clave)} es la que frena dos reintentos simultaneos: el
 * segundo choca contra el indice y se rechaza con 409.</p>
 *
 * <p>No es parte del dominio de turnos: es un registro tecnico, pero vive en
 * {@code modelo} porque es una entidad JPA como las demas.</p>
 */
@Entity
@Table(name = "claves_idempotencia",
        uniqueConstraints = @UniqueConstraint(name = "uk_idempotencia_usuario_clave",
                columnNames = {"usuario", "clave"}))
public class RegistroIdempotencia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String usuario;

    @Column(nullable = false, length = 255)
    private String clave;

    @Column(nullable = false, length = 64)
    private String huella;

    /** Turno creado con esta clave; {@code null} si el pedido no llego a crearlo. */
    @Column(name = "turno_id")
    private Long turnoId;

    @Column(name = "creada_en", nullable = false)
    private LocalDateTime creadaEn;

    public RegistroIdempotencia() {
    }

    public RegistroIdempotencia(String usuario, String clave, String huella, LocalDateTime creadaEn) {
        this.usuario = usuario;
        this.clave = clave;
        this.huella = huella;
        this.creadaEn = creadaEn;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsuario() {
        return usuario;
    }

    public void setUsuario(String usuario) {
        this.usuario = usuario;
    }

    public String getClave() {
        return clave;
    }

    public void setClave(String clave) {
        this.clave = clave;
    }

    public String getHuella() {
        return huella;
    }

    public void setHuella(String huella) {
        this.huella = huella;
    }

    public Long getTurnoId() {
        return turnoId;
    }

    public void setTurnoId(Long turnoId) {
        this.turnoId = turnoId;
    }

    public LocalDateTime getCreadaEn() {
        return creadaEn;
    }

    public void setCreadaEn(LocalDateTime creadaEn) {
        this.creadaEn = creadaEn;
    }
}
