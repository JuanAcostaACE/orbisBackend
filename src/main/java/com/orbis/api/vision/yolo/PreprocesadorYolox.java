package com.orbis.api.vision.yolo;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * Preprocesado de YOLOX (yolox.data.data_augment.preproc del repo oficial):
 * letterbox arriba-izquierda sobre lienzo 416x416 relleno con 114, canales BGR (convención cv2),
 * layout NCHW, float32 en rango 0-255 SIN media ni desviación.
 *
 * El redimensionado usa Graphics2D bilineal; no es bit a bit igual a cv2.resize INTER_LINEAR.
 */
public final class PreprocesadorYolox {

    private static final int RELLENO = 114;

    private PreprocesadorYolox() {
    }

    /** Tensor NCHW [1, 3, 416, 416] en orden plano, y escala aplicada (original -> entrada). */
    public record Entrada(float[] datos, double ratio) {
    }

    public static Entrada letterbox(BufferedImage imagen) {
        int tam = PostprocesadorYolox.TAMANO_ENTRADA;
        int ancho = imagen.getWidth();
        int alto = imagen.getHeight();
        double ratio = Math.min(tam / (double) alto, tam / (double) ancho);
        int nuevoAncho = (int) (ancho * ratio);
        int nuevoAlto = (int) (alto * ratio);

        BufferedImage lienzo = new BufferedImage(tam, tam, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = lienzo.createGraphics();
        try {
            g.setColor(new Color(RELLENO, RELLENO, RELLENO));
            g.fillRect(0, 0, tam, tam);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(imagen, 0, 0, nuevoAncho, nuevoAlto, null);
        } finally {
            g.dispose();
        }

        int area = tam * tam;
        float[] datos = new float[3 * area];
        for (int y = 0; y < tam; y++) {
            for (int x = 0; x < tam; x++) {
                int rgb = lienzo.getRGB(x, y);
                int pos = y * tam + x;
                datos[pos] = rgb & 0xFF;                 // B
                datos[area + pos] = (rgb >> 8) & 0xFF;   // G
                datos[2 * area + pos] = (rgb >> 16) & 0xFF; // R
            }
        }
        return new Entrada(datos, ratio);
    }
}
