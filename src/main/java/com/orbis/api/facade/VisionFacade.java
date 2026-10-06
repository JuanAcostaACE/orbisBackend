package com.orbis.api.facade;

import java.util.List;

/**
 * Patrón Facade + Adapter:
 * Desacopla ActivoStrategy de la implementación concreta de Google Cloud Vision.
 * Si en el futuro se cambia el proveedor de IA, solo se cambia el Adapter,
 * no las estrategias ni el servicio.
 */
public interface VisionFacade {

    /**
     * Analiza una imagen y retorna lista de etiquetas descriptivas.
     *
     * @param imagenBase64 Imagen codificada en Base64 (sin el prefijo data:image/...)
     * @return Lista de etiquetas detectadas por la IA (ej: ["Chair", "Furniture", "Wood"])
     * @throws VisionException Si la API externa falla o la imagen no es válida
     */
    List<String> analizarImagen(String imagenBase64);
}
