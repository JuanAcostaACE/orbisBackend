package com.orbis.api.model;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "obstacle_types")
public class ObstacleType {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String label; // ej: "Silla", "Escaleras"
    
    private String dangerLevel; // "ALTO", "MEDIO", "BAJO"
}