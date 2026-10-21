package com.orbis.api.model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Entidad de usuario del sistema.
 * La contraseña se guarda como hash BCrypt (nunca en texto plano).
 */
@Data
@Entity
@Table(name = "usuarios")
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 60)
    private String nombre;

    @Column(unique = true, nullable = false, length = 120)
    private String correo;

    /** Hash BCrypt. NUNCA se expone en respuestas al cliente. */
    @Column(nullable = false)
    private String hashContrasena;

    @Column(nullable = false)
    private boolean activo = true;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime fechaCreacion;
}
