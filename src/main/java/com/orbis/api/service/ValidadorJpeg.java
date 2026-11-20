package com.orbis.api.service;

import org.springframework.http.HttpStatus;

/**
 * Validación de una captura JPEG antes de aceptarla (docs/ORBIS_DEVICE_PROTOCOL.md, secciones 2 y 4).
 * No decodifica la imagen: solo comprueba tipo, tamaño y marcadores de inicio y fin.
 */
public final class ValidadorJpeg {

    public static final String CONTENT_TYPE = "image/jpeg";
    public static final int TAMANO_MINIMO_BYTES = 5 * 1024;
    public static final int TAMANO_MAXIMO_BYTES = 512 * 1024;

    private ValidadorJpeg() {
    }

    /** Lanza CapturaException si el tipo de contenido no es image/jpeg (415). */
    public static void validarTipo(String contentType) {
        if (contentType == null || !contentType.toLowerCase().startsWith(CONTENT_TYPE)) {
            throw new CapturaException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "TIPO_NO_SOPORTADO");
        }
    }

    /**
     * Lanza CapturaException si el cuerpo no es una captura aceptable:
     * demasiado grande (413), demasiado pequeño o sin marcadores JPEG (422).
     */
    public static void validarCuerpo(byte[] cuerpo) {
        if (cuerpo == null || cuerpo.length > TAMANO_MAXIMO_BYTES) {
            throw new CapturaException(HttpStatus.PAYLOAD_TOO_LARGE, "IMAGEN_DEMASIADO_GRANDE");
        }
        if (cuerpo.length < TAMANO_MINIMO_BYTES) {
            throw new CapturaException(HttpStatus.UNPROCESSABLE_ENTITY, "IMAGEN_NO_VALIDA");
        }
        boolean inicioJpeg = (cuerpo[0] & 0xFF) == 0xFF && (cuerpo[1] & 0xFF) == 0xD8;
        boolean finJpeg = (cuerpo[cuerpo.length - 2] & 0xFF) == 0xFF
                && (cuerpo[cuerpo.length - 1] & 0xFF) == 0xD9;
        if (!inicioJpeg || !finJpeg) {
            throw new CapturaException(HttpStatus.UNPROCESSABLE_ENTITY, "IMAGEN_NO_VALIDA");
        }
    }
}
