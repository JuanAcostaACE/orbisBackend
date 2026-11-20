package com.orbis.api.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Bastón registrado con identidad propia (docs/ORBIS_DEVICE_PROTOCOL.md, sección 1).
 * El token nunca se guarda en claro: solo su hash SHA-256.
 */
@Data
@Entity
@Table(name = "dispositivos")
public class Dispositivo {

    public enum Estado { ACTIVO, REVOCADO }

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "propietario_correo", nullable = false, length = 255)
    private String propietarioCorreo;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Estado estado;

    @Column(name = "modelo_hardware", length = 60)
    private String modeloHardware;

    @Column(name = "version_firmware", length = 20)
    private String versionFirmware;

    @Column(name = "creado_en", nullable = false)
    private LocalDateTime creadoEn;

    @Column(name = "token_valido_hasta", nullable = false)
    private LocalDateTime tokenValidoHasta;
}
