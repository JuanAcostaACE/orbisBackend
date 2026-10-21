package com.orbis.api.dto;

import java.time.LocalDateTime;

/**
 * DTO de salida para eventos.
 * Excluye imagenUrl (puede ser base64 de megabytes) y la relación JPA TipoObstaculo.
 * Expone únicamente lo que el frontend necesita para mostrar el historial.
 */
public record EventoResponse(
        Long          id,
        String        modo,
        Double        distanciaCm,
        String        etiquetasIA,
        LocalDateTime fechaHora,
        String        obstaculoEtiqueta,   // null si no está clasificado
        String        obstaculoNivel       // null si no está clasificado
) {}
