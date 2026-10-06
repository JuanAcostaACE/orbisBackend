package com.orbis.api.facade;

/**
 * Excepción específica del Facade de Vision.
 * Permite que ActivoStrategy distinga errores de la IA externa
 * de errores propios de la aplicación.
 */
public class VisionException extends RuntimeException {

    public VisionException(String mensaje) {
        super(mensaje);
    }

    public VisionException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
