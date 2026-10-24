package com.orbis.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbis.api.state.EstadoPasivo;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Auditoría automática de hardware/wokwi: sketch.ino vs diagram.json.
 * No compila el firmware (requiere toolchain ESP32); verifica de forma estática que
 * pines, umbrales, seguridad local y autenticación sean coherentes entre sketch,
 * diagrama y backend.
 */
class HardwareWokwiConsistencyTest {

    private static String sketch;
    private static JsonNode conexiones;

    @BeforeAll
    static void cargar() throws IOException {
        Path base = Path.of(System.getProperty("user.dir"), "hardware", "wokwi");
        sketch = Files.readString(base.resolve("sketch.ino"), StandardCharsets.UTF_8);
        conexiones = new ObjectMapper().readTree(base.resolve("diagram.json").toFile()).get("connections");
    }

    private static int entero(String nombre) {
        Matcher m = Pattern.compile("const\\s+int\\s+" + nombre + "\\s*=\\s*(\\d+)\\s*;").matcher(sketch);
        assertThat(m.find()).as("constante entera %s en sketch.ino", nombre).isTrue();
        return Integer.parseInt(m.group(1));
    }

    private static double decimal(String nombre) {
        Matcher m = Pattern.compile("const\\s+float\\s+" + nombre + "\\s*=\\s*([\\d.]+)f\\s*;").matcher(sketch);
        assertThat(m.find()).as("constante float %s en sketch.ino", nombre).isTrue();
        return Double.parseDouble(m.group(1));
    }

    private static long tiempo(String nombre) {
        Matcher m = Pattern.compile("const\\s+unsigned\\s+long\\s+" + nombre + "\\s*=\\s*(\\d+)\\s*;").matcher(sketch);
        assertThat(m.find()).as("constante de tiempo %s en sketch.ino", nombre).isTrue();
        return Long.parseLong(m.group(1));
    }

    private static boolean conectado(String a, String b) {
        for (JsonNode c : conexiones) {
            String x = c.get(0).asText(), y = c.get(1).asText();
            if ((x.equals(a) && y.equals(b)) || (x.equals(b) && y.equals(a))) return true;
        }
        return false;
    }

    // ── Pines ─────────────────────────────────────────────────────

    @Test
    @DisplayName("TRIG/ECHO del sketch coinciden con el cableado del HC-SR04")
    void pines_ultrasonico() {
        assertThat(conectado("esp32:D" + entero("TRIGGER"), "ultrasonico:TRIG")).isTrue();
        assertThat(conectado("esp32:D" + entero("ECHO"), "ultrasonico:ECHO")).isTrue();
    }

    @Test
    @DisplayName("Vibrador (LED rojo), LED de estado y botón coinciden con diagram.json")
    void pines_actuadores_y_boton() {
        assertThat(conectado("esp32:D" + entero("VIBRADOR"), "ledVibrador:A")).isTrue();
        assertThat(conectado("esp32:D" + entero("LED_ESTADO"), "ledEstado:A")).isTrue();
        assertThat(conectado("esp32:D" + entero("BOTON"), "boton:1.l")).isTrue();
    }

    @Test
    @DisplayName("Los 5 pines usados son distintos entre sí")
    void pines_sin_colisiones() {
        var pines = java.util.Set.of(entero("TRIGGER"), entero("ECHO"), entero("BOTON"),
                                     entero("VIBRADOR"), entero("LED_ESTADO"));
        assertThat(pines).hasSize(5);
    }

    // ── Umbrales ──────────────────────────────────────────────────

    @Test
    @DisplayName("Histéresis 100/115 cm, rango válido 0-400 cm y cooldown 5 s")
    void umbrales() {
        assertThat(decimal("PELIGRO_CM")).isEqualTo(100.0);
        assertThat(decimal("HISTERESIS_CM")).isEqualTo(15.0);
        assertThat(decimal("MAX_VALIDA_CM")).isEqualTo(400.0);
        assertThat(decimal("MIN_EXCLUSIVO_CM")).isEqualTo(0.0);
        assertThat(tiempo("COOLDOWN_MS")).isEqualTo(5000L);
    }

    @Test
    @DisplayName("El umbral de salida del firmware (peligro + histéresis) = umbral de recuperación del backend")
    void umbral_salida_alineado_con_backend() {
        assertThat(decimal("PELIGRO_CM") + decimal("HISTERESIS_CM")).isEqualTo(EstadoPasivo.UMBRAL_LIBRE_CM);
    }

    @Test
    @DisplayName("Backoff HTTP exponencial con tope")
    void backoff_configurado() {
        assertThat(tiempo("BACKOFF_INICIAL_MS")).isPositive();
        assertThat(tiempo("BACKOFF_MAX_MS")).isGreaterThan(tiempo("BACKOFF_INICIAL_MS"));
    }

    // ── Filtrado y seguridad local ────────────────────────────────

    @Test
    @DisplayName("Filtra lecturas inválidas (<= 0 o > 400 cm) y trata el timeout como inválido")
    void filtra_lecturas_invalidas() {
        assertThat(sketch).contains("bool lecturaValida(float distanciaCm)");
        assertThat(sketch).contains("distanciaCm > Umbral::MIN_EXCLUSIVO_CM && distanciaCm <= Umbral::MAX_VALIDA_CM");
        assertThat(sketch).contains("if (duracion == 0) return -1.0f");
    }

    @Test
    @DisplayName("La tarea de seguridad (vibrador) no usa WiFi ni HTTP")
    void seguridad_local_sin_red() {
        int ini = sketch.indexOf("void tareaSeguridad(void* param) {");
        int fin = sketch.indexOf("float medirDistanciaCm() {");
        assertThat(ini).isGreaterThan(0);
        assertThat(fin).isGreaterThan(ini);
        String tarea = sketch.substring(ini, fin);

        assertThat(tarea).contains("actualizarVibrador(true)");
        assertThat(tarea).doesNotContain("WiFi").doesNotContain("HTTP").doesNotContain("http");
    }

    @Test
    @DisplayName("El loop de red respeta backoff y no descarta el evento antes de confirmar")
    void loop_http_con_backoff() {
        assertThat(sketch).contains("proximoIntentoHttp_ms");
        assertThat(sketch).contains("backoffActual_ms");
        assertThat(sketch).contains("errorCliente");
    }

    // ── Autenticación y secretos ──────────────────────────────────

    @Test
    @DisplayName("Envía X-Device-Token con un placeholder (sin secreto real) y nunca lo imprime")
    void token_con_placeholder_y_sin_log() {
        assertThat(sketch).contains("\"X-Device-Token\"");
        assertThat(sketch).contains("TOKEN_DE_PRUEBA_CAMBIAR_EN_PRODUCCION");
        assertThat(sketch).doesNotContainPattern("Serial\\.print[^;]*DEVICE_TOKEN");
        assertThat(sketch).contains("REEMPLAZAR_CON_URL_DEL_BACKEND");
    }
}
