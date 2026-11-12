package com.mediconecta.modelo;

import jakarta.json.bind.annotation.JsonbTransient;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Cuenta con la que se inicia sesion.
 *
 * <p>Desde la Entrega 2 la autenticacion es por <b>JWT</b>: la aplicacion valida
 * usuario y contrasena contra esta tabla y emite un token firmado con los roles.
 * La contrasena <b>nunca</b> se guarda en claro: {@code hashClave} es un hash
 * PBKDF2 con sal propia ({@code seguridad.HashDeClaves}).</p>
 *
 * <p>El nombre de usuario es el mismo que usan {@code Paciente.usuario} y
 * {@code Profesional.usuario} para las comprobaciones de ownership, asi que esa
 * asociacion no cambio respecto de la Entrega 1.</p>
 */
@Entity
@Table(name = "usuarios")
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 60)
    private String usuario;

    @JsonbTransient
    @Column(name = "hash_clave", nullable = false, length = 200)
    private String hashClave;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "usuario_roles", joinColumns = @JoinColumn(name = "usuario_id"))
    @Column(name = "rol", nullable = false, length = 20)
    private Set<String> roles = new LinkedHashSet<>();

    @Column(nullable = false)
    private boolean activo = true;

    public Usuario() {
    }

    public Usuario(String usuario, String hashClave, Set<String> roles) {
        this.usuario = usuario;
        this.hashClave = hashClave;
        this.roles = new LinkedHashSet<>(roles);
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

    public String getHashClave() {
        return hashClave;
    }

    public void setHashClave(String hashClave) {
        this.hashClave = hashClave;
    }

    public Set<String> getRoles() {
        return roles;
    }

    public void setRoles(Set<String> roles) {
        this.roles = roles;
    }

    public boolean isActivo() {
        return activo;
    }

    public void setActivo(boolean activo) {
        this.activo = activo;
    }
}
