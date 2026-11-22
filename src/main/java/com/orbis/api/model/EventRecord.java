package com.orbis.api.model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "event_records")
public class EventRecord {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String mode; // "PASSIVE" o "ACTIVE"

    private Double distanceCm;
    
    @Column(columnDefinition = "TEXT")
    private String imageUrl; // O base64 temporal para el Avance 1

    @Column(columnDefinition = "TEXT")
    private String aiLabels; // Etiquetas del modelo de IA (MobileNet/navegador o Hugging Face)

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "obstacle_type_id")
    private ObstacleType obstacle;

    /**
     * Usuario propietario del evento.
     * Null solo para eventos del ESP32 autenticados por token de dispositivo
     * (se resuelve en DeviceTokenFilter antes de llegar al controller).
     * INSERT_ONLY: el backend lo asigna; el cliente no puede forzarlo.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", updatable = false)
    private UserAccount owner;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}