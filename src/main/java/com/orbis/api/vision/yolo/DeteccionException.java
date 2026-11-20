package com.orbis.api.vision.yolo;

/**
 * Fallo controlado del pipeline de detección. El motivo es un código corto que se guarda
 * en la captura como motivo_fallo (p. ej. IA_NO_DISPONIBLE, ERROR_INFERENCIA).
 */
public class DeteccionException extends RuntimeException {

    private final String motivo;

    public DeteccionException(String motivo, Throwable causa) {
        super(motivo, causa);
        this.motivo = motivo;
    }

    public String getMotivo() {
        return motivo;
    }
}
