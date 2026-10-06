package com.smartcane.api.model;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "tipo_obstaculo")
public class TipoObstaculo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String etiqueta; // ej: "Silla", "Escaleras"
    
    private String nivelPeligro; // "ALTO", "MEDIO", "BAJO"
}