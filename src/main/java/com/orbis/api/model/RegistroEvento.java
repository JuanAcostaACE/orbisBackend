package com.orbis.api.model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "registro_eventos")
public class RegistroEvento {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String modo; // "PASIVO" o "ACTIVO"

    private Double distanciaCm;
    
    @Column(columnDefinition = "TEXT")
    private String imagenUrl; // O base64 temporal para el Avance 1

    @Column(columnDefinition = "TEXT")
    private String etiquetasIA; // Etiquetas del modelo de IA (MobileNet/navegador o Hugging Face)

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tipo_obstaculo_id")
    private TipoObstaculo obstaculo;

    /**
     * Usuario propietario del evento.
     * Null solo para eventos del ESP32 autenticados por token de dispositivo
     * (se resuelve en DeviceTokenFilter antes de llegar al controller).
     * INSERT_ONLY: el backend lo asigna; el cliente no puede forzarlo.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", updatable = false)
    private Usuario propietario;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime fechaHora;
}