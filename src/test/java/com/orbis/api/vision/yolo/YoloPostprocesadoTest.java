package com.orbis.api.vision.yolo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Postprocesado YOLOX con tensores sintéticos: umbral, NMS y mapeo COCO. */
class YoloPostprocesadoTest {

    private static final int TAM = 416;
    private static final int CLASES = 80;
    private static final int STRIDE5 = 5 + CLASES;
    private static final int ANCHAS = PostprocesadorYolox.numeroAnclas(TAM); // 3549

    /** Índice de ancla dentro del stride 8 (grilla 52x52). */
    private static int anclaStride8(int gx, int gy) {
        return gy * 52 + gx;
    }

    private static float[] salidaVacia() {
        return new float[ANCHAS * STRIDE5];
    }

    /** Escribe una ancla: offsets (tx, ty), log-tamaños (tw, th), objectness y una clase. */
    private static void escribir(float[] s, int ancla, float tx, float ty, float tw, float th,
                                 float objectness, int clase, float probClase) {
        int o = ancla * STRIDE5;
        s[o] = tx;
        s[o + 1] = ty;
        s[o + 2] = tw;
        s[o + 3] = th;
        s[o + 4] = objectness;
        s[o + 5 + clase] = probClase;
    }

    private static List<Deteccion> decodificar(float[] s) {
        return PostprocesadorYolox.decodificar(s, TAM, CLASES, 1.0, TAM, TAM,
                PostprocesadorYolox.UMBRAL_CONFIANZA, PostprocesadorYolox.UMBRAL_IOU);
    }

    @Test
    @DisplayName("Confianza por debajo de 0,45 se descarta (objectness o clase)")
    void bajo_umbral_se_descarta() {
        float[] s = salidaVacia();
        escribir(s, anclaStride8(5, 5), 0, 0, 0f, 0f, 0.30f, 0, 0.95f); // score 0,285
        escribir(s, anclaStride8(6, 6), 0, 0, 0f, 0f, 0.95f, 2, 0.30f); // score 0,285
        assertTrue(decodificar(s).isEmpty());
    }

    @Test
    @DisplayName("Confianza igual o superior a 0,45 se conserva con su clase COCO")
    void sobre_umbral_se_conserva() {
        float[] s = salidaVacia();
        escribir(s, anclaStride8(5, 5), 0, 0, 0f, 0f, 0.90f, 0, 0.80f); // persona, score 0,72
        List<Deteccion> r = decodificar(s);
        assertEquals(1, r.size());
        assertEquals(ClaseCoco.PERSONA, r.get(0).clase());
        assertEquals(0.72f, r.get(0).confianza(), 1e-4f);
    }

    @Test
    @DisplayName("NMS elimina la caja solapada de la misma clase y conserva la de mayor score")
    void nms_elimina_solapados() {
        float[] s = salidaVacia();
        // Dos anclas vecinas con cajas casi idénticas (tamaño 10 px, centros a 0,8 px de distancia).
        float tamanoLog = (float) Math.log(10.0 / 8.0);
        escribir(s, anclaStride8(0, 0), 0f, 0f, tamanoLog, tamanoLog, 0.90f, 0, 0.80f); // score 0,72
        escribir(s, anclaStride8(1, 0), -0.9f, 0f, tamanoLog, tamanoLog, 0.80f, 0, 0.70f); // score 0,56
        // Una tercera persona lejos del grupo no debe ser suprimida.
        escribir(s, anclaStride8(10, 10), 0f, 0f, 0f, 0f, 0.90f, 0, 0.80f);

        List<Deteccion> r = decodificar(s);
        assertEquals(2, r.size(), "debe quedar una del grupo solapado y la lejana");
        assertEquals(0.72f, r.get(0).confianza(), 1e-4f);
        assertTrue(r.stream().allMatch(d -> d.clase() == ClaseCoco.PERSONA));
    }

    @Test
    @DisplayName("Índice de salida con tamaño incorrecto se rechaza")
    void tamano_incorrecto_rechazado() {
        assertThrows(IllegalArgumentException.class, () -> decodificar(new float[10]));
    }

    @Test
    @DisplayName("Mapeo COCO: 80 clases en orden de YOLOX, etiquetas en español y sin duplicados")
    void mapeo_coco_correcto() {
        ClaseCoco[] todas = ClaseCoco.values();
        assertEquals(80, todas.length);
        assertEquals(ClaseCoco.PERSONA, ClaseCoco.desde(0));
        assertEquals("persona", ClaseCoco.desde(0).etiqueta());
        assertEquals(ClaseCoco.COCHE, ClaseCoco.desde(2));
        assertEquals("coche", ClaseCoco.desde(2).etiqueta());
        assertEquals("bicicleta", ClaseCoco.desde(1).etiqueta());
        assertEquals("cepillo de dientes", ClaseCoco.desde(79).etiqueta());
        assertThrows(IllegalArgumentException.class, () -> ClaseCoco.desde(80));
        long distintas = java.util.Arrays.stream(todas).map(ClaseCoco::etiqueta).distinct().count();
        assertEquals(80, distintas);
        assertTrue(java.util.Arrays.stream(todas).noneMatch(c -> c.etiqueta().contains("puerta")),
                "COCO no tiene puerta");
    }

    @Test
    @DisplayName("Clase detectada en la salida se traduce a su etiqueta COCO")
    void clase_de_salida_se_traduce() {
        float[] s = salidaVacia();
        escribir(s, anclaStride8(20, 20), 0f, 0f, 0f, 0f, 0.90f, 2, 0.90f); // coche, score 0,81
        List<Deteccion> r = decodificar(s);
        assertEquals(1, r.size());
        assertEquals("coche", r.get(0).clase().etiqueta());
    }
}
