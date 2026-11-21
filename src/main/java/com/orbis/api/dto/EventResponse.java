package com.orbis.api.dto;

import java.time.LocalDateTime;

/**
 * DTO de salida para eventos.
 * Excluye imageUrl (puede ser base64 de megabytes) y la relación JPA ObstacleType.
 * Expone únicamente lo que el frontend necesita para mostrar el historial.
 */
public record EventResponse(
        Long          id,
        String        mode,
        Double        distanceCm,
        String        aiLabels,
        LocalDateTime createdAt,
        String        obstacleLabel,   // null si no está clasificado
        String        obstacleLevel       // null si no está clasificado
) {}
