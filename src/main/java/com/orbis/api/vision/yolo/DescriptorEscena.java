package com.orbis.api.vision.yolo;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Descripción en español generada por reglas. Solo afirma lo que el detector ha visto:
 * no dice posición, distancia ni "camino despejado".
 *
 * Orden: las clases con más detecciones primero y, a igualdad, por el orden de COCO.
 */
public final class DescriptorEscena {

    public static final String SIN_OBJETOS = "No se han identificado objetos con seguridad en la imagen.";

    public record Descripcion(String texto, List<String> etiquetas) {
    }

    private DescriptorEscena() {
    }

    public static Descripcion describir(List<Deteccion> detecciones) {
        if (detecciones == null || detecciones.isEmpty()) {
            return new Descripcion(SIN_OBJETOS, List.of());
        }

        Map<ClaseCoco, Long> conteo = detecciones.stream().collect(Collectors.groupingBy(
                Deteccion::clase,
                () -> new EnumMap<>(ClaseCoco.class),
                Collectors.counting()));

        List<Map.Entry<ClaseCoco, Long>> orden = new ArrayList<>(conteo.entrySet());
        orden.sort((a, b) -> {
            int porCantidad = Long.compare(b.getValue(), a.getValue());
            return porCantidad != 0 ? porCantidad : a.getKey().compareTo(b.getKey());
        });

        List<String> partes = new ArrayList<>();
        List<String> etiquetas = new ArrayList<>();
        for (Map.Entry<ClaseCoco, Long> e : orden) {
            ClaseCoco clase = e.getKey();
            long n = e.getValue();
            etiquetas.add(clase.etiqueta());
            if (n == 1) {
                partes.add((clase.esFemenino() ? "una " : "un ") + clase.etiqueta());
            } else {
                partes.add(n + " " + clase.plural());
            }
        }

        return new Descripcion("Hay " + unir(partes) + ".", etiquetas);
    }

    private static String unir(List<String> partes) {
        if (partes.size() == 1) {
            return partes.get(0);
        }
        int ultima = partes.size() - 1;
        return String.join(", ", partes.subList(0, ultima)) + " y " + partes.get(ultima);
    }
}
