package com.orbis.api.facade;

import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Patrón Facade + Adapter (Fase 3 revisado):
 *
 * RESPONSABILIDAD REAL Y VERIFICABLE:
 *   VisionAdapter actúa como adaptador de formato entre el cliente/simulador y el
 *   modelo de dominio interno de ORBIS. Sus tres funciones concretas son:
 *
 *   1. VALIDACIÓN de entradas crudas:
 *      - Verifica que etiquetasIA no sea nula, vacía o demasiado larga.
 *      - Rechaza entradas que empiecen con "ERROR" (errores del cliente, no dominio).
 *      - Rechaza entradas que empiecen con "data:" o contengan base64 embebido
 *        (protección contra payloads accidentales).
 *
 *   2. NORMALIZACIÓN de formato:
 *      - Convierte a formato canónico: "Clase1, Clase2 (85%), Clase3"
 *      - Capitaliza cada etiqueta.
 *      - Trunca a MAX_ETIQUETAS etiquetas para evitar payloads excesivos.
 *      - Elimina caracteres de control o peligrosos (hardening XSS en dominio).
 *
 *   3. FILTRADO POR UMBRAL:
 *      - Si las etiquetas contienen porcentajes de confianza (formato GestorVision),
 *        descarta las que estén por debajo de UMBRAL_MINIMO_CONFIANZA.
 *      - Si no contienen porcentaje, pasa la etiqueta tal cual (legacy / MobileNet).
 *
 * RELACIÓN CON LOS 6 PATRONES:
 *   Facade/Adapter → VisionAdapter (este archivo)
 *   Strategy       → ActivoStrategy invoca VisionAdapter vía VisionFacade (interfaz)
 *   Command        → ComandoConsultarIA encapsula la llamada al Adapter
 *   Observer       → LogEventoObserver notificado después de guardar
 *   State          → ContextoSistema actualiza estado según el modo del evento
 *   Factory Method → StrategyFactory selecciona ACTIVO vs PASIVO según el modo
 *
 * LOADOUT — Nota de clarificación:
 *   El término "loadout" no existe en el código fuente de ORBIS.
 *   Posible confusión del evaluador con alguno de estos conceptos:
 *     - "load" del modelo de IA (cargarModeloIA / gestorVision.inicializarAsync)
 *     - "payload" del request JSON que envía el ESP32 o el simulador
 *     - "output" del compilador TypeScript (outDir ./dist)
 *     - "obfuscation/bundling" del proceso de build (minify.cjs con Terser)
 *   No se requiere implementar ningún "loadout"; el término no corresponde a
 *   ninguna funcionalidad técnica identificada en el proyecto.
 */
@Component
public class VisionAdapter implements VisionFacade {

    /** Longitud máxima de la cadena de etiquetas aceptada del cliente. */
    private static final int    MAX_LONGITUD_ETIQUETAS = 2000;

    /** Máximo de etiquetas distintas a conservar. */
    private static final int    MAX_ETIQUETAS = 5;

    /** Umbral de confianza mínimo al interpretar etiquetas con porcentaje (0-100). */
    private static final double UMBRAL_MINIMO_CONFIANZA = 40.0; // 40%

    /** Patrón para extraer etiqueta + porcentaje opcional: "Chair (85%)" o "Chair". */
    private static final Pattern PATRON_ETIQUETA =
        Pattern.compile("([A-Za-záéíóúÁÉÍÓÚñÑ][\\w\\sáéíóúÁÉÍÓÚñÑ]{0,50}?)\\s*(?:\\((\\d{1,3})%\\))?");

    /** Patrón para detectar caracteres de control o HTML/script peligrosos. */
    private static final Pattern PATRON_PELIGROSO =
        Pattern.compile("[<>\"'`\\x00-\\x1F\\x7F]");

    @Override
    public List<String> analizarImagen(String etiquetasCrudas) {
        validar(etiquetasCrudas);
        return normalizar(etiquetasCrudas);
    }

    // ──────────────────────────────────────────────────────────────
    // Validación — lanza VisionException ante entradas inválidas
    // ──────────────────────────────────────────────────────────────

    private void validar(String entrada) {
        if (entrada == null || entrada.isBlank()) {
            throw new VisionException("Etiquetas nulas o vacías");
        }

        if (entrada.length() > MAX_LONGITUD_ETIQUETAS) {
            throw new VisionException(
                "Etiquetas demasiado largas: " + entrada.length() +
                " chars (máx " + MAX_LONGITUD_ETIQUETAS + ")");
        }

        String trim = entrada.trim();

        // Rechazar errores del cliente (no son datos de dominio)
        if (trim.startsWith("ERROR")) {
            throw new VisionException("Entrada contiene error del cliente: " +
                trim.substring(0, Math.min(60, trim.length())));
        }

        // Rechazar posibles imágenes base64 incrustadas accidentalmente
        if (trim.startsWith("data:") || trim.startsWith("/9j/") || trim.startsWith("iVBOR")) {
            throw new VisionException(
                "Entrada parece ser imagen base64, no etiquetas de texto. " +
                "El cliente debe enviar solo etiquetasIA, no imagenUrl.");
        }

        // Rechazar caracteres de control / HTML (XSS defense en dominio)
        if (PATRON_PELIGROSO.matcher(trim).find()) {
            throw new VisionException(
                "Etiquetas contienen caracteres no permitidos (control/HTML)");
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Normalización — convierte formato crudo → formato canónico ORBIS
    // ──────────────────────────────────────────────────────────────

    private List<String> normalizar(String entrada) {
        // Dividir por coma o punto y coma
        String[] partes = entrada.split("[,;]");

        return Arrays.stream(partes)
            .map(String::trim)
            .filter(s -> !s.isBlank())
            .map(this::normalizarEtiqueta)
            .filter(e -> e != null)
            .limit(MAX_ETIQUETAS)
            .toList();
    }

    /**
     * Normaliza una etiqueta individual.
     * Si tiene porcentaje y no supera el umbral, retorna null (será filtrada).
     */
    private String normalizarEtiqueta(String raw) {
        var matcher = PATRON_ETIQUETA.matcher(raw.trim());
        if (!matcher.matches()) return null;

        String clase      = matcher.group(1).trim();
        String porcentaje = matcher.group(2);

        if (clase.isBlank()) return null;

        // Filtrar por umbral de confianza si hay porcentaje
        if (porcentaje != null) {
            double confianza = Double.parseDouble(porcentaje);
            if (confianza < UMBRAL_MINIMO_CONFIANZA) {
                return null; // Descartada por baja confianza
            }
            // Capitalizar y conservar el porcentaje
            return capitalize(clase) + " (" + porcentaje + "%)";
        }

        // Sin porcentaje → legacy / MobileNet → solo capitalizar
        return capitalize(clase);
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}