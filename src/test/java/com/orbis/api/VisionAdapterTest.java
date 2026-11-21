package com.orbis.api;

import com.orbis.api.facade.VisionAdapter;
import com.orbis.api.facade.VisionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests unitarios del VisionAdapter.
 *
 * Verifica:
 *  - Validación: null, blank, demasiado largo, ERROR, base64, chars peligrosos.
 *  - Normalización: capitalización, límite de etiquetas, formato canónico.
 *  - Filtrado por umbral de confianza (etiquetas con porcentaje).
 *  - Etiquetas legacy sin porcentaje (compatibilidad MobileNet anterior).
 */
class VisionAdapterTest {

    private VisionAdapter adapter;

    @BeforeEach
    void setUp() { adapter = new VisionAdapter(); }

    // ── Validación ─────────────────────────────────────────────

    @Test
    @DisplayName("null → VisionException")
    void input_null() {
        assertThatThrownBy(() -> adapter.analyzeImage(null))
            .isInstanceOf(VisionException.class)
            .hasMessageContaining("nulas o vacías");
    }

    @Test
    @DisplayName("blank → VisionException")
    void input_empty() {
        assertThatThrownBy(() -> adapter.analyzeImage("   "))
            .isInstanceOf(VisionException.class);
    }

    @Test
    @DisplayName("demasiado larga (>2000) → VisionException")
    void input_too_long() {
        String longValue = "A".repeat(2001);
        assertThatThrownBy(() -> adapter.analyzeImage(longValue))
            .isInstanceOf(VisionException.class)
            .hasMessageContaining("demasiado largas");
    }

    @Test
    @DisplayName("empieza con ERROR → VisionException")
    void input_client_error() {
        assertThatThrownBy(() -> adapter.analyzeImage("ERROR_IA: failed to load"))
            .isInstanceOf(VisionException.class)
            .hasMessageContaining("error del cliente");
    }

    @Test
    @DisplayName("parece base64 data: → VisionException")
    void input_base64_data() {
        assertThatThrownBy(() -> adapter.analyzeImage("data:image/jpeg;base64,/9j/abc"))
            .isInstanceOf(VisionException.class)
            .hasMessageContaining("base64");
    }

    @Test
    @DisplayName("contiene HTML → VisionException")
    void input_dangerous_html() {
        assertThatThrownBy(() -> adapter.analyzeImage("<script>alert(1)</script>"))
            .isInstanceOf(VisionException.class)
            .hasMessageContaining("caracteres no permitidos");
    }

    // ── Normalización ──────────────────────────────────────────

    @Test
    @DisplayName("etiquetas simples → capitalización y lista")
    void simple_normalization() {
        List<String> outcome = adapter.analyzeImage("chair, table, person");
        assertThat(outcome).containsExactly("Chair", "Table", "Person");
    }

    @Test
    @DisplayName("más de 5 etiquetas → truncado a 5")
    void labels_limit() {
        String many = "a, b, c, d, e, f, g, h";
        List<String> outcome = adapter.analyzeImage(many);
        assertThat(outcome.size()).isLessThanOrEqualTo(5);
    }

    @Test
    @DisplayName("etiquetas con porcentaje → conservadas si sobre umbral")
    void labels_with_percentage_above_threshold() {
        List<String> outcome = adapter.analyzeImage("Chair (85%), Table (60%)");
        assertThat(outcome).containsExactly("Chair (85%)", "Table (60%)");
    }

    @Test
    @DisplayName("etiquetas con porcentaje < 40% → filtradas")
    void labels_with_percentage_below_threshold() {
        List<String> outcome = adapter.analyzeImage("Chair (85%), Noise (30%), Table (70%)");
        assertThat(outcome).doesNotContain("Noise (30%)");
        assertThat(outcome).containsExactlyInAnyOrder("Chair (85%)", "Table (70%)");
    }

    @Test
    @DisplayName("etiquetas sin porcentaje → formato legacy MobileNet")
    void legacy_mobilenet_labels() {
        List<String> outcome = adapter.analyzeImage("tabby cat, coffee mug");
        assertThat(outcome).containsExactly("Tabby cat", "Coffee mug");
    }

    @Test
    @DisplayName("formato COCO-SSD mixto → normalizado correctamente")
    void coco_ssd_format() {
        List<String> outcome = adapter.analyzeImage("Person (92%), Chair (78%), Laptop (65%)");
        assertThat(outcome).containsExactly("Person (92%)", "Chair (78%)", "Laptop (65%)");
    }
}
