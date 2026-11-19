package com.orbis.api.vision.yolo;

/**
 * Una detección tras NMS. Las coordenadas están en píxeles de la imagen original.
 * Sin distancia: el modelo 2D no mide profundidad.
 */
public record Deteccion(ClaseCoco clase, float confianza, float x1, float y1, float x2, float y2) {
}
