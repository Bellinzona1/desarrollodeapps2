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
 * Un mensaje JMS que un consumidor ya proceso (consumidor idempotente).
 *
 * <p>JMS garantiza entrega "al menos una vez": si el servidor se cae despues
 * de procesar un mensaje pero antes de confirmarlo, lo vuelve a entregar. Cada
 * MDB registra aca la clave de negocio del mensaje ({@code turno-15-confirmado})
 * en la <b>misma transaccion</b> en la que lo procesa; si el mensaje llega de
 * nuevo, encuentra la clave y lo descarta sin repetir el efecto. La
 * restriccion unica {@code (consumidor, clave)} cubre el caso de dos entregas
 * simultaneas.</p>
 */
@Entity
@Table(name = "mensajes_procesados",
        uniqueConstraints = @UniqueConstraint(name = "uk_mensaje_consumidor_clave",
                columnNames = {"consumidor", "clave"}))
public class MensajeProcesado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 60)
    private String consumidor;

    @Column(nullable = false, length = 120)
    private String clave;

    @Column(name = "procesado_en", nullable = false)
    private LocalDateTime procesadoEn;

    @Column(length = 300)
    private String detalle;

    public MensajeProcesado() {
    }

    public MensajeProcesado(String consumidor, String clave, LocalDateTime procesadoEn, String detalle) {
        this.consumidor = consumidor;
        this.clave = clave;
        this.procesadoEn = procesadoEn;
        this.detalle = detalle;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getConsumidor() {
        return consumidor;
    }

    public void setConsumidor(String consumidor) {
        this.consumidor = consumidor;
    }

    public String getClave() {
        return clave;
    }

    public void setClave(String clave) {
        this.clave = clave;
    }

    public LocalDateTime getProcesadoEn() {
        return procesadoEn;
    }

    public void setProcesadoEn(LocalDateTime procesadoEn) {
        this.procesadoEn = procesadoEn;
    }

    public String getDetalle() {
        return detalle;
    }

    public void setDetalle(String detalle) {
        this.detalle = detalle;
    }
}
