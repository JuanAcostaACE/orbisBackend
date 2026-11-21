package com.orbis.api.facade;

/**
 * Excepción específica del Facade de Vision.
 * Permite que ActiveStrategy distinga errores de la IA externa
 * de errores propios de la aplicación.
 */
public class VisionException extends RuntimeException {

    public VisionException(String message) {
        super(message);
    }

    public VisionException(String message, Throwable cause) {
        super(message, cause);
    }
}
