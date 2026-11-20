package com.orbis.api.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Token de acceso de la app móvil (Bearer, sin cookies). Se devuelve una sola vez;
 * en BD solo se guarda su SHA-256 hex.
 */
@Data
@Entity
@Table(name = "tokens_sesion_movil")
public class SesionMovil {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "correo", nullable = false, length = 255)
    private String correo;

    @Column(name = "creado_en", nullable = false)
    private LocalDateTime creadoEn;

    @Column(name = "expira_en", nullable = false)
    private LocalDateTime expiraEn;

    @Column(name = "revocado", nullable = false)
    private boolean revocado;
}
