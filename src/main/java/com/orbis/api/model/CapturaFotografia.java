package com.orbis.api.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Captura fotográfica enviada por un bastón. Solo guarda metadatos: los bytes de la
 * imagen no se persisten en esta fase (ver docs/ORBIS_NEXT_STEPS.md, pendiente de
 * decisión de almacenamiento).
 *
 * Idempotencia: (propietario_correo, capture_id) es único. Un reintento con el mismo
 * contenido devuelve la captura existente en lugar de crear otra.
 */
@Data
@Entity
@Table(
        name = "capturas_fotografia",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_captura_propietario_id",
                columnNames = {"propietario_correo", "capture_id"}))
public class CapturaFotografia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "capture_id", nullable = false, length = 36)
    private String captureId;

    @Column(name = "propietario_correo", nullable = false, length = 255)
    private String propietarioCorreo;

    @Column(nullable = false, length = 64)
    private String sha256;

    @Column(name = "tamano_bytes", nullable = false)
    private int tamanoBytes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoCaptura estado;

    /** Motivo cuando el estado es FALLIDA (p. ej. REINICIO_DURANTE_PROCESO, IA_NO_DISPONIBLE). */
    @Column(name = "motivo_fallo", length = 60)
    private String motivoFallo;

    @Column(name = "recibida_en", nullable = false)
    private LocalDateTime recibidaEn;

    @Column(name = "actualizada_en", nullable = false)
    private LocalDateTime actualizadaEn;
}
