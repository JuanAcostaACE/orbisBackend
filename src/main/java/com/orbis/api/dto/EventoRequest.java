package com.orbis.api.dto;

import jakarta.validation.constraints.*;

/**
 * DTO de entrada para POST /api/v1/eventos.
 * Sustituye el uso directo de la entidad JPA RegistroEvento en el controller.
 * La entidad nunca sale del service; el cliente no puede forzar id, fechaHora ni obstaculo.
 */
public record EventoRequest(

        @NotBlank(message = "El campo 'modo' es obligatorio")
        @Pattern(regexp = "PASIVO|ACTIVO", message = "El modo debe ser PASIVO o ACTIVO")
        String modo,

        @NotNull(message = "El campo 'distanciaCm' es obligatorio")
        @DecimalMin(value = "0.0",  message = "La distancia no puede ser negativa")
        @DecimalMax(value = "500.0", message = "La distancia no puede superar 500 cm")
        Double distanciaCm,

        @Size(max = 2000, message = "Las etiquetas IA no pueden superar 2000 caracteres")
        String etiquetasIA   // opcional; null si es modo PASIVO

) {}
