package com.orbis.api.vision.yolo;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;

/**
 * Decodifica bytes JPEG sin lanzar excepciones. Devuelve null si la imagen no se puede leer.
 * Comprueba las dimensiones antes de decodificar para evitar imágenes "bomba" que agoten la memoria.
 */
public final class DecodificadorImagen {

    /** Límite de píxeles (20 MP). Una captura de 512 KB nunca debería acercarse. */
    static final long MAX_PIXELES = 20_000_000L;

    private DecodificadorImagen() {
    }

    public static BufferedImage decodificar(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        try (ImageInputStream flujo = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (flujo == null) {
                return null;
            }
            Iterator<ImageReader> lectores = ImageIO.getImageReaders(flujo);
            if (!lectores.hasNext()) {
                return null;
            }
            ImageReader lector = lectores.next();
            try {
                lector.setInput(flujo);
                long ancho = lector.getWidth(0);
                long alto = lector.getHeight(0);
                if (ancho <= 0 || alto <= 0 || ancho * alto > MAX_PIXELES) {
                    return null;
                }
                return lector.read(0);
            } finally {
                lector.dispose();
            }
        } catch (IOException | RuntimeException e) {
            // Imagen corrupta o no decodificable: el llamador la marca como FALLIDA.
            return null;
        }
    }
}
