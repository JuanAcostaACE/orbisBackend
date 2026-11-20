package com.orbis.api.model;

import java.util.Set;

/**
 * Estados de una captura fotográfica (docs/ORBIS_DEVICE_PROTOCOL.md, sección 6).
 *
 * Transiciones permitidas:
 *   RECIBIDA   → EN_PROCESO | FALLIDA
 *   EN_PROCESO → DESCRITA   | FALLIDA
 *   DESCRITA   → ENTREGADA
 *   FALLIDA, ENTREGADA → (terminales)
 *
 * Repetir el mismo estado no es una transición y se trata como idempotente.
 */
public enum EstadoCaptura {

    RECIBIDA,
    EN_PROCESO,
    DESCRITA,
    ENTREGADA,
    FALLIDA;

    private Set<EstadoCaptura> siguientes() {
        return switch (this) {
            case RECIBIDA   -> Set.of(EN_PROCESO, FALLIDA);
            case EN_PROCESO -> Set.of(DESCRITA, FALLIDA);
            case DESCRITA   -> Set.of(ENTREGADA);
            case ENTREGADA, FALLIDA -> Set.of();
        };
    }

    /** true si pasar de {@code this} a {@code destino} es válido o es el mismo estado. */
    public boolean puedePasarA(EstadoCaptura destino) {
        return destino == this || siguientes().contains(destino);
    }
}
