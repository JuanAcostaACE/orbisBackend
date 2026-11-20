package com.orbis.api.vision.yolo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DescriptorEscenaTest {

    private static Deteccion det(ClaseCoco c) {
        return new Deteccion(c, 0.9f, 0, 0, 10, 10);
    }

    @Test
    @DisplayName("Sin detecciones: DESCRITA con frase honesta, nunca 'camino despejado'")
    void sin_detecciones() {
        DescriptorEscena.Descripcion d = DescriptorEscena.describir(List.of());
        assertEquals("No se han identificado objetos con seguridad en la imagen.", d.texto());
        assertTrue(d.etiquetas().isEmpty());
        assertFalse(d.texto().toLowerCase().contains("despejado"));
    }

    @Test
    @DisplayName("Una persona: 'Hay una persona.'")
    void una_persona() {
        DescriptorEscena.Descripcion d = DescriptorEscena.describir(List.of(det(ClaseCoco.PERSONA)));
        assertEquals("Hay una persona.", d.texto());
        assertEquals(List.of("persona"), d.etiquetas());
    }

    @Test
    @DisplayName("Varias de la misma clase: plural con número")
    void varias_personas() {
        List<Deteccion> dets = new ArrayList<>();
        dets.add(det(ClaseCoco.PERSONA));
        dets.add(det(ClaseCoco.PERSONA));
        assertEquals("Hay 2 personas.", DescriptorEscena.describir(dets).texto());
    }

    @Test
    @DisplayName("Dos clases: artículo por género y 'y' antes de la última")
    void dos_clases() {
        List<Deteccion> dets = List.of(det(ClaseCoco.COCHE), det(ClaseCoco.PERSONA));
        DescriptorEscena.Descripcion d = DescriptorEscena.describir(dets);
        // Empate en cantidad: orden por clase COCO (persona antes que coche).
        assertEquals("Hay una persona y un coche.", d.texto());
        assertEquals(List.of("persona", "coche"), d.etiquetas());
    }

    @Test
    @DisplayName("La clase más frecuente va primero")
    void mas_frecuente_primero() {
        List<Deteccion> dets = List.of(det(ClaseCoco.COCHE), det(ClaseCoco.COCHE), det(ClaseCoco.PERSONA));
        assertEquals("Hay 2 coches y una persona.", DescriptorEscena.describir(dets).texto());
    }
}
