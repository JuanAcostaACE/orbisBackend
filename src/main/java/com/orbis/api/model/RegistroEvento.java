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
    private String etiquetasIA; // JSON con las etiquetas retornadas por Google Vision

    @ManyToOne
    @JoinColumn(name = "tipo_obstaculo_id")
    private TipoObstaculo obstaculo;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime fechaHora;
}