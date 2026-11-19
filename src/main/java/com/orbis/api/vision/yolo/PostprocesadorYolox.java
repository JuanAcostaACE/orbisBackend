package com.orbis.api.vision.yolo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Decodificación de la salida cruda de YOLOX (convención de demo_postprocess del repo oficial).
 *
 * La salida del modelo es [1, N, 5 + clases] con N = 52² + 26² + 13² = 3549 para entrada 416.
 * Por ancla: (tx, ty, tw, th, objectness, p_clase...). objectness y p_clase ya llevan sigmoide.
 *
 *   cx = (tx + gx) * stride    cy = (ty + gy) * stride
 *   w  = exp(tw) * stride      h  = exp(th) * stride
 *   score = objectness * p_clase, descartado si &lt; umbral de confianza
 *   NMS por clase con umbral de IoU; las cajas se devuelven en píxeles de la imagen original.
 *
 * Clase pura, sin estado: se prueba con tensores sintéticos.
 */
public final class PostprocesadorYolox {

    public static final int TAMANO_ENTRADA = 416;
    public static final int[] STRIDES = {8, 16, 32};
    public static final float UMBRAL_CONFIANZA = 0.45f;
    public static final float UMBRAL_IOU = 0.45f;

    private PostprocesadorYolox() {
    }

    public static int numeroAnclas(int tamano) {
        int total = 0;
        for (int stride : STRIDES) {
            int lado = tamano / stride;
            total += lado * lado;
        }
        return total;
    }

    /**
     * @param salida      valores planos de [N, 5 + numClases] en orden fila-mayor
     * @param tamano      lado de la entrada del modelo (416)
     * @param ratio       escala del letterbox (original -> entrada)
     * @param ancho       ancho de la imagen original (recorte de cajas)
     * @param alto        alto de la imagen original
     */
    public static List<Deteccion> decodificar(float[] salida, int tamano, int numClases, double ratio,
                                              int ancho, int alto,
                                              float umbralConfianza, float umbralIou) {
        int anclas = numeroAnclas(tamano);
        int stride5 = 5 + numClases;
        if (salida.length != anclas * stride5) {
            throw new IllegalArgumentException("Salida YOLOX inesperada: " + salida.length
                    + " valores, se esperaban " + (anclas * stride5));
        }

        Map<Integer, List<Candidato>> porClase = new HashMap<>();
        int a = 0;
        for (int stride : STRIDES) {
            int lado = tamano / stride;
            for (int gy = 0; gy < lado; gy++) {
                for (int gx = 0; gx < lado; gx++, a++) {
                    int o = a * stride5;
                    float objectness = salida[o + 4];
                    if (objectness < umbralConfianza) {
                        continue; // score = objectness * p_clase <= objectness: descarte seguro
                    }
                    double cx = (salida[o] + gx) * stride;
                    double cy = (salida[o + 1] + gy) * stride;
                    double w = Math.exp(salida[o + 2]) * stride;
                    double h = Math.exp(salida[o + 3]) * stride;
                    float x1 = (float) (cx - w / 2), y1 = (float) (cy - h / 2);
                    float x2 = (float) (cx + w / 2), y2 = (float) (cy + h / 2);
                    for (int c = 0; c < numClases; c++) {
                        float score = objectness * salida[o + 5 + c];
                        if (score < umbralConfianza) {
                            continue;
                        }
                        porClase.computeIfAbsent(c, k -> new ArrayList<>())
                                .add(new Candidato(x1, y1, x2, y2, score));
                    }
                }
            }
        }

        List<Deteccion> resultado = new ArrayList<>();
        for (Map.Entry<Integer, List<Candidato>> entrada : porClase.entrySet()) {
            ClaseCoco clase = ClaseCoco.desde(entrada.getKey());
            for (Candidato k : nms(entrada.getValue(), umbralIou)) {
                float x1 = recortar(k.x1() / (float) ratio, ancho);
                float y1 = recortar(k.y1() / (float) ratio, alto);
                float x2 = recortar(k.x2() / (float) ratio, ancho);
                float y2 = recortar(k.y2() / (float) ratio, alto);
                if (x2 <= x1 || y2 <= y1) {
                    continue;
                }
                resultado.add(new Deteccion(clase, k.score(), x1, y1, x2, y2));
            }
        }
        resultado.sort(Comparator.comparingDouble(Deteccion::confianza).reversed());
        return resultado;
    }

    private record Candidato(float x1, float y1, float x2, float y2, float score) {
    }

    /** NMS de una clase, con la aritmética de yolox.utils.nms (áreas con +1). */
    static List<Candidato> nms(List<Candidato> candidatos, float umbralIou) {
        List<Candidato> orden = new ArrayList<>(candidatos);
        orden.sort(Comparator.comparingDouble(Candidato::score).reversed());
        List<Candidato> conservados = new ArrayList<>();
        boolean[] suprimido = new boolean[orden.size()];
        for (int i = 0; i < orden.size(); i++) {
            if (suprimido[i]) {
                continue;
            }
            Candidato a = orden.get(i);
            conservados.add(a);
            float areaA = (a.x2() - a.x1() + 1) * (a.y2() - a.y1() + 1);
            for (int j = i + 1; j < orden.size(); j++) {
                if (suprimido[j]) {
                    continue;
                }
                Candidato b = orden.get(j);
                float xx1 = Math.max(a.x1(), b.x1()), yy1 = Math.max(a.y1(), b.y1());
                float xx2 = Math.min(a.x2(), b.x2()), yy2 = Math.min(a.y2(), b.y2());
                float inter = Math.max(0f, xx2 - xx1 + 1) * Math.max(0f, yy2 - yy1 + 1);
                float areaB = (b.x2() - b.x1() + 1) * (b.y2() - b.y1() + 1);
                float iou = inter / (areaA + areaB - inter);
                if (iou > umbralIou) {
                    suprimido[j] = true;
                }
            }
        }
        return conservados;
    }

    private static float recortar(float valor, int limite) {
        return Math.max(0f, Math.min(limite, valor));
    }
}
