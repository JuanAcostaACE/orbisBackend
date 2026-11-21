package com.orbis.api.dto;

import jakarta.validation.constraints.*;

/**
 * DTO de entrada para POST /api/v1/events.
 * Sustituye el uso directo de la entidad JPA EventRecord en el controller.
 * La entidad nunca sale del service; el cliente no puede forzar id, createdAt ni obstaculo.
 */
public record EventRequest(

        @NotBlank(message = "El campo 'mode' es obligatorio")
        @Pattern(regexp = "PASSIVE|ACTIVE", message = "El modo debe ser PASSIVE o ACTIVE")
        String mode,

        @NotNull(message = "El campo 'distanceCm' es obligatorio")
        @DecimalMin(value = "0.0",  message = "La distancia no puede ser negativa")
        @DecimalMax(value = "500.0", message = "La distancia no puede superar 500 cm")
        Double distanceCm,

        @Size(max = 2000, message = "Las etiquetas IA no pueden superar 2000 caracteres")
        String aiLabels   // opcional; null si es modo PASSIVE

) {}
