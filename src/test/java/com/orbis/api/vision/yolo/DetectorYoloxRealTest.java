package com.orbis.api.vision.yolo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Inferencia REAL con models/yolox_nano.onnx en CPU. Sin mocks. */
public class DetectorYoloxRealTest {

    public static BufferedImage imagenDePrueba(int ancho, int alto) {
        BufferedImage img = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, ancho, alto);
        g.setColor(new Color(200, 40, 40));
        g.fillRect(ancho / 5, alto / 6, ancho / 3, alto / 2);
        g.dispose();
        // Ruido determinista para que el JPEG no sea trivialmente pequeño.
        java.util.Random r = new java.util.Random(42);
        for (int y = 0; y < alto; y++) {
            for (int x = 0; x < ancho; x++) {
                if (r.nextInt(4) == 0) {
                    img.setRGB(x, y, r.nextInt(0xFFFFFF));
                }
            }
        }
        return img;
    }

    public static byte[] comoJpeg(BufferedImage img) throws IOException {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(img, "jpg", salida));
        return salida.toByteArray();
    }

    @Test
    @DisplayName("Pipeline real: JPEG -> letterbox -> ONNX -> postprocesado, sin excepción")
    void pipeline_real_sin_excepcion() throws IOException {
        DetectorYolox detector = new DetectorYolox();
        BufferedImage img = DecodificadorImagen.decodificar(comoJpeg(imagenDePrueba(640, 480)));
        assertNotNull(img, "el JPEG de prueba debe decodificarse");

        List<Deteccion> detecciones = detector.detectar(img);
        assertNotNull(detecciones);
        for (Deteccion d : detecciones) {
            assertTrue(d.confianza() >= PostprocesadorYolox.UMBRAL_CONFIANZA);
            assertNotNull(d.clase());
            assertTrue(d.x2() > d.x1() && d.y2() > d.y1());
        }
        System.out.println("[YOLOX] detecciones en imagen de prueba: " + detecciones.size());
        detector.cerrar();
    }

    @Test
    @DisplayName("Tiempo de inferencia en CPU (media de 20 ejecuciones tras calentamiento)")
    void tiempo_de_inferencia() throws IOException {
        DetectorYolox detector = new DetectorYolox();
        BufferedImage img = DecodificadorImagen.decodificar(comoJpeg(imagenDePrueba(640, 480)));

        long t0 = System.nanoTime();
        detector.detectar(img); // incluye carga del modelo
        long primeraMs = (System.nanoTime() - t0) / 1_000_000;

        for (int i = 0; i < 3; i++) {
            detector.detectar(img); // calentamiento
        }
        int n = 20;
        long inicio = System.nanoTime();
        for (int i = 0; i < n; i++) {
            detector.detectar(img);
        }
        double mediaMs = (System.nanoTime() - inicio) / 1_000_000.0 / n;
        System.out.printf("[YOLOX] primera llamada (incluye carga): %d ms; media por inferencia: %.1f ms%n",
                primeraMs, mediaMs);
        assertTrue(mediaMs > 0);
        detector.cerrar();
    }

    @Test
    @DisplayName("Preprocesado: tensor NCHW 3x416x416 y escala de letterbox correcta")
    void preprocesado_forma() {
        BufferedImage img = imagenDePrueba(640, 480);
        PreprocesadorYolox.Entrada e = PreprocesadorYolox.letterbox(img);
        assertEquals(3 * 416 * 416, e.datos().length);
        assertEquals(416.0 / 640.0, e.ratio(), 1e-9);
        // Zona de relleno inferior (la imagen 640x480 ocupa 416x312 arriba-izquierda): valor 114 en BGR.
        int pos = (415) * 416 + 0;
        assertEquals(114f, e.datos()[pos], 0f);
        assertEquals(114f, e.datos()[416 * 416 + pos], 0f);
        assertEquals(114f, e.datos()[2 * 416 * 416 + pos], 0f);
    }
}
