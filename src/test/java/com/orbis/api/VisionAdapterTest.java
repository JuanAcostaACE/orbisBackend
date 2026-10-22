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
    void entrada_nula() {
        assertThatThrownBy(() -> adapter.analizarImagen(null))
            .isInstanceOf(VisionException.class)
            .hasMessageContaining("nulas o vacías");
    }

    @Test
    @DisplayName("blank → VisionException")
    void entrada_vacia() {
        assertThatThrownBy(() -> adapter.analizarImagen("   "))
            .isInstanceOf(VisionException.class);
    }

    @Test
    @DisplayName("demasiado larga (>2000) → VisionException")
    void entrada_demasiado_larga() {
        String larga = "A".repeat(2001);
        assertThatThrownBy(() -> adapter.analizarImagen(larga))
            .isInstanceOf(VisionException.class)
            .hasMessageContaining("demasiado largas");
    }

    @Test
    @DisplayName("empieza con ERROR → VisionException")
    void entrada_error_cliente() {
        assertThatThrownBy(() -> adapter.analizarImagen("ERROR_IA: failed to load"))
            .isInstanceOf(VisionException.class)
            .hasMessageContaining("error del cliente");
    }

    @Test
    @DisplayName("parece base64 data: → VisionException")
    void entrada_base64_data() {
        assertThatThrownBy(() -> adapter.analizarImagen("data:image/jpeg;base64,/9j/abc"))
            .isInstanceOf(VisionException.class)
            .hasMessageContaining("base64");
    }

    @Test
    @DisplayName("contiene HTML → VisionException")
    void entrada_html_peligroso() {
        assertThatThrownBy(() -> adapter.analizarImagen("<script>alert(1)</script>"))
            .isInstanceOf(VisionException.class)
            .hasMessageContaining("caracteres no permitidos");
    }

    // ── Normalización ──────────────────────────────────────────

    @Test
    @DisplayName("etiquetas simples → capitalización y lista")
    void normalizacion_simple() {
        List<String> resultado = adapter.analizarImagen("chair, table, person");
        assertThat(resultado).containsExactly("Chair", "Table", "Person");
    }

    @Test
    @DisplayName("más de 5 etiquetas → truncado a 5")
    void limite_etiquetas() {
        String muchas = "a, b, c, d, e, f, g, h";
        List<String> resultado = adapter.analizarImagen(muchas);
        assertThat(resultado.size()).isLessThanOrEqualTo(5);
    }

    @Test
    @DisplayName("etiquetas con porcentaje → conservadas si sobre umbral")
    void etiquetas_con_porcentaje_sobre_umbral() {
        List<String> resultado = adapter.analizarImagen("Chair (85%), Table (60%)");
        assertThat(resultado).containsExactly("Chair (85%)", "Table (60%)");
    }

    @Test
    @DisplayName("etiquetas con porcentaje < 40% → filtradas")
    void etiquetas_con_porcentaje_bajo_umbral() {
        List<String> resultado = adapter.analizarImagen("Chair (85%), Noise (30%), Table (70%)");
        assertThat(resultado).doesNotContain("Noise (30%)");
        assertThat(resultado).containsExactlyInAnyOrder("Chair (85%)", "Table (70%)");
    }

    @Test
    @DisplayName("etiquetas sin porcentaje → formato legacy MobileNet")
    void etiquetas_legacy_mobilenet() {
        List<String> resultado = adapter.analizarImagen("tabby cat, coffee mug");
        assertThat(resultado).containsExactly("Tabby cat", "Coffee mug");
    }

    @Test
    @DisplayName("formato COCO-SSD mixto → normalizado correctamente")
    void formato_coco_ssd() {
        List<String> resultado = adapter.analizarImagen("Person (92%), Chair (78%), Laptop (65%)");
        assertThat(resultado).containsExactly("Person (92%)", "Chair (78%)", "Laptop (65%)");
    }
}
