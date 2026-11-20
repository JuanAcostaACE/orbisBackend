package com.orbis.api.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Código de un solo uso que el usuario genera en la app para registrar un bastón.
 * Solo se guarda su hash. Caduca y se marca como usado al registrarse.
 */
@Data
@Entity
@Table(name = "codigos_vinculacion")
public class CodigoVinculacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "codigo_hash", nullable = false, unique = true, length = 64)
    private String codigoHash;

    @Column(name = "propietario_correo", nullable = false, length = 255)
    private String propietarioCorreo;

    @Column(name = "expira_en", nullable = false)
    private LocalDateTime expiraEn;

    @Column(nullable = false)
    private boolean usado;
}
