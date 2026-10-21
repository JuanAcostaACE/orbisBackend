package com.orbis.api.dto;

import java.util.List;

/**
 * Respuesta paginada genérica.
 *
 * Contrato de paginación del API:
 *   GET /api/v1/eventos?page=0&size=20
 *
 * Parámetros:
 *   page  — número de página basado en 0 (default: 0)
 *   size  — elementos por página (default: 20, máximo: 100)
 *
 * Campos de respuesta:
 *   contenido      — lista de EventoResponse de esta página
 *   paginaActual   — número de página actual (0-indexed)
 *   tamanioPagina  — tamaño de página solicitado
 *   totalElementos — total de eventos del usuario (todas las páginas)
 *   totalPaginas   — número total de páginas
 *   primera        — true si es la primera página
 *   ultima         — true si es la última página
 */
public record PaginaResponse<T>(
        List<T>  contenido,
        int      paginaActual,
        int      tamanioPagina,
        long     totalElementos,
        int      totalPaginas,
        boolean  primera,
        boolean  ultima
) {
    /** Construye desde un {@link org.springframework.data.domain.Page}. */
    public static <T> PaginaResponse<T> of(org.springframework.data.domain.Page<T> page) {
        return new PaginaResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast()
        );
    }
}
