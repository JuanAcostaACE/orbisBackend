package com.orbis.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbis.api.state.PassiveState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Auditoría automática de hardware/wokwi: sketch.ino vs diagram.json.
 * No compila el firmware (requiere toolchain ESP32); verifica de forma estática que
 * pines, umbrales, seguridad local y autenticación sean coherentes entre sketch,
 * diagrama y backend.
 */
class HardwareWokwiConsistencyTest {

    private static String sketch;
    private static JsonNode connections;

    /** Literales de sketch.ino que parecen tokens pero son placeholders permitidos. */
    private static final Set<String> ALLOWED_LITERALS = Set.of("REPLACE_WITH_BACKEND_URL");

    /** SHA-256 y longitud del token histórico filtrado (no se almacena el valor en claro). */
    private static final String HISTORICAL_TOKEN_HASH =
            "2cf6010ff38b8b55e34b6fd4b97db9c66b0fcda61ee2b64367929f70ebf85598";
    private static final int HISTORICAL_TOKEN_LENGTH = 37;

    @BeforeAll
    static void load() throws IOException {
        Path base = hardware();
        sketch = Files.readString(base.resolve("sketch.ino"), StandardCharsets.UTF_8);
        connections = new ObjectMapper().readTree(base.resolve("diagram.json").toFile()).get("connections");
    }

    private static Path hardware() {
        return Path.of(System.getProperty("user.dir"), "hardware", "wokwi");
    }

    /** Contenido de todos los literales de cadena ("...") de un fuente C/C++ (sin escapes). */
    private static List<String> literals(String sourceText) {
        List<String> out = new ArrayList<>();
        Matcher m = Pattern.compile("\"([^\"\\\\]*)\"").matcher(sourceText);
        while (m.find()) out.add(m.group(1));
        return out;
    }

    /**
     * Forma de un token de dispositivo: 20+ caracteres URL-safe con letras Y dígitos.
     * Un token generado con secrets.token_urlsafe(32) cumple esto con altísima probabilidad.
     * Textos como "REPLACE_WITH_BACKEND_URL" o "Wokwi-GUEST" no cumplen.
     */
    private static boolean looksLikeToken(String s) {
        return s.matches("[A-Za-z0-9_\\-]{20,}")
                && s.chars().anyMatch(Character::isDigit)
                && s.chars().anyMatch(Character::isLetter);
    }

    private static int intConst(String name) {
        Matcher m = Pattern.compile("const\\s+int\\s+" + name + "\\s*=\\s*(\\d+)\\s*;").matcher(sketch);
        assertThat(m.find()).as("constante entera %s en sketch.ino", name).isTrue();
        return Integer.parseInt(m.group(1));
    }

    private static double decimal(String name) {
        Matcher m = Pattern.compile("const\\s+float\\s+" + name + "\\s*=\\s*([\\d.]+)f\\s*;").matcher(sketch);
        assertThat(m.find()).as("constante float %s en sketch.ino", name).isTrue();
        return Double.parseDouble(m.group(1));
    }

    private static long timeConst(String name) {
        Matcher m = Pattern.compile("const\\s+unsigned\\s+long\\s+" + name + "\\s*=\\s*(\\d+)\\s*;").matcher(sketch);
        assertThat(m.find()).as("constante de tiempo %s en sketch.ino", name).isTrue();
        return Long.parseLong(m.group(1));
    }

    private static boolean connected(String a, String b) {
        for (JsonNode c : connections) {
            String x = c.get(0).asText(), y = c.get(1).asText();
            if ((x.equals(a) && y.equals(b)) || (x.equals(b) && y.equals(a))) return true;
        }
        return false;
    }

    // ── Pines ─────────────────────────────────────────────────────

    @Test
    @DisplayName("TRIG/ECHO del sketch coinciden con el cableado del HC-SR04")
    void ultrasonic_pins() {
        assertThat(connected("esp32:D" + intConst("TRIGGER"), "ultrasonic:TRIG")).isTrue();
        assertThat(connected("esp32:D" + intConst("ECHO"), "ultrasonic:ECHO")).isTrue();
    }

    @Test
    @DisplayName("Vibrador (LED rojo), LED de estado y botón coinciden con diagram.json")
    void actuator_and_button_pins() {
        assertThat(connected("esp32:D" + intConst("VIBRATOR"), "vibratorLed:A")).isTrue();
        assertThat(connected("esp32:D" + intConst("STATUS_LED"), "statusLed:A")).isTrue();
        assertThat(connected("esp32:D" + intConst("BUTTON"), "button:1.l")).isTrue();
    }

    @Test
    @DisplayName("Los 5 pines usados son distintos entre sí")
    void pins_without_collisions() {
        var pins = java.util.Set.of(intConst("TRIGGER"), intConst("ECHO"), intConst("BUTTON"),
                                     intConst("VIBRATOR"), intConst("STATUS_LED"));
        assertThat(pins).hasSize(5);
    }

    // ── Umbrales ──────────────────────────────────────────────────

    @Test
    @DisplayName("Histéresis 100/115 cm, rango válido 0-400 cm y cooldown 5 s")
    void thresholds() {
        assertThat(decimal("DANGER_CM")).isEqualTo(100.0);
        assertThat(decimal("HYSTERESIS_CM")).isEqualTo(15.0);
        assertThat(decimal("MAX_VALID_CM")).isEqualTo(400.0);
        assertThat(decimal("MIN_EXCLUSIVE_CM")).isEqualTo(0.0);
        assertThat(timeConst("COOLDOWN_MS")).isEqualTo(5000L);
    }

    @Test
    @DisplayName("El umbral de salida del firmware (peligro + histéresis) = umbral de recuperación del backend")
    void exit_threshold_aligned_with_backend() {
        assertThat(decimal("DANGER_CM") + decimal("HYSTERESIS_CM")).isEqualTo(PassiveState.CLEAR_THRESHOLD_CM);
    }

    @Test
    @DisplayName("Backoff HTTP exponencial con tope")
    void backoff_configured() {
        assertThat(timeConst("BACKOFF_INITIAL_MS")).isPositive();
        assertThat(timeConst("BACKOFF_MAX_MS")).isGreaterThan(timeConst("BACKOFF_INITIAL_MS"));
    }

    // ── Filtrado y seguridad local ────────────────────────────────

    @Test
    @DisplayName("Filtra lecturas inválidas (<= 0 o > 400 cm) y trata el timeout como inválido")
    void filters_invalid_readings() {
        assertThat(sketch).contains("bool isValidReading(float distanceCm)");
        assertThat(sketch).contains("distanceCm > Threshold::MIN_EXCLUSIVE_CM && distanceCm <= Threshold::MAX_VALID_CM");
        assertThat(sketch).contains("if (duration == 0) return -1.0f");
    }

    @Test
    @DisplayName("La tarea de seguridad (vibrador) no usa WiFi ni HTTP")
    void local_safety_without_network() {
        int start = sketch.indexOf("void safetyTask(void* param) {");
        int end = sketch.indexOf("float measureDistanceCm() {");
        assertThat(start).isGreaterThan(0);
        assertThat(end).isGreaterThan(start);
        String task = sketch.substring(start, end);

        assertThat(task).contains("updateVibrator(true)");
        assertThat(task).doesNotContain("WiFi").doesNotContain("HTTP").doesNotContain("http");
    }

    @Test
    @DisplayName("El loop de red respeta backoff y no descarta el evento antes de confirmar")
    void http_loop_with_backoff() {
        assertThat(sketch).contains("nextHttpAttempt_ms");
        assertThat(sketch).contains("currentBackoff_ms");
        assertThat(sketch).contains("clientError");
    }

    // ── Autenticación y secretos ──────────────────────────────────

    @Test
    @DisplayName("sketch.ino incluye secrets.h, envía X-Device-Token desde ahí y no contiene ningún token literal")
    void token_read_from_secrets_h() {
        assertThat(sketch).contains("#include \"secrets.h\"");
        assertThat(sketch).contains("#if __has_include(\"secrets.h\")");
        assertThat(sketch).contains("DEVICE_TOKEN = SECRET_DEVICE_TOKEN");
        assertThat(sketch).contains("\"X-Device-Token\", Cfg::DEVICE_TOKEN");

        List<String> literalTokens = literals(sketch).stream()
                .filter(HardwareWokwiConsistencyTest::looksLikeToken)
                .toList();
        assertThat(literalTokens).as("literales con forma de token en sketch.ino").isEmpty();

        assertThat(sketch).doesNotContainPattern("Serial\\.print[^;]*DEVICE_TOKEN");
        assertThat(sketch).contains("REPLACE_WITH_BACKEND_URL");
    }

    @Test
    @DisplayName("Sin secrets.h el firmware no compila: la rama #else es un #error, sin valor por defecto")
    void without_secrets_h_fails_to_compile() {
        Pattern error = Pattern.compile("#else\\s*\\R\\s*#error \"Falta hardware/wokwi/secrets\\.h: "
                + "copia secrets\\.h\\.example y define SECRET_DEVICE_TOKEN \\(no usar valores por defecto\\)\"");
        assertThat(error.matcher(sketch).find()).as("#error en la rama #else de sketch.ino").isTrue();
        assertThat(sketch).doesNotContain("#define SECRET_DEVICE_TOKEN");
    }

    @Test
    @DisplayName("sketch.ino no tiene ningún literal con forma de token (20+ caracteres), salvo el placeholder de URL")
    void sketch_without_token_shaped_literal() {
        List<String> suspicious = literals(sketch).stream()
                .filter(s -> s.matches("[A-Za-z0-9_\\-]{20,}") && !ALLOWED_LITERALS.contains(s))
                .toList();
        assertThat(suspicious).as("literales con forma de token en sketch.ino").isEmpty();
    }

    /**
     * El token de prueba histórico (que estuvo en 812923d y 584f629) NO se escribe aquí: solo se
     * guarda su SHA-256 y su longitud. Se comprueba que ninguna subcadena de esa longitud dentro de
     * hardware/ o src/ (salvo este test) tenga ese hash.
     */
    @Test
    @DisplayName("El token de prueba histórico no aparece en hardware/ ni en src/ (comprobado por hash)")
    void historical_token_does_not_reappear() throws IOException, NoSuchAlgorithmException {
        Path root = Path.of(System.getProperty("user.dir"));
        List<Path> files;
        try (Stream<Path> a = Files.walk(root.resolve("hardware"));
             Stream<Path> b = Files.walk(root.resolve("src"))) {
            files = Stream.concat(a, b)
                    .filter(Files::isRegularFile)
                    .filter(p -> !p.getFileName().toString().equals("HardwareWokwiConsistencyTest.java"))
                    .toList();
        }

        Pattern runResult = Pattern.compile("[A-Za-z0-9_\\-]{" + HISTORICAL_TOKEN_LENGTH + ",}");
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        List<Path> matchesFound = new ArrayList<>();
        for (Path p : files) {
            String text = new String(Files.readAllBytes(p), StandardCharsets.ISO_8859_1);
            Matcher m = runResult.matcher(text);
            boolean found = false;
            while (!found && m.find()) {
                String run = m.group();
                for (int i = 0; i + HISTORICAL_TOKEN_LENGTH <= run.length() && !found; i++) {
                    String window = run.substring(i, i + HISTORICAL_TOKEN_LENGTH);
                    byte[] d = sha.digest(window.getBytes(StandardCharsets.ISO_8859_1));
                    found = HexFormat.of().formatHex(d).equals(HISTORICAL_TOKEN_HASH);
                }
            }
            if (found) matchesFound.add(p);
        }
        assertThat(matchesFound).as("archivos que contienen el token histórico").isEmpty();
    }

    @Test
    @DisplayName("secrets.h.example solo contiene un placeholder, nunca un token")
    void secrets_example_placeholder_only() throws IOException {
        String example = Files.readString(hardware().resolve("secrets.h.example"), StandardCharsets.UTF_8);

        Matcher m = Pattern.compile("#define\\s+SECRET_DEVICE_TOKEN\\s+\"([^\"]*)\"").matcher(example);
        assertThat(m.find()).as("define SECRET_DEVICE_TOKEN en secrets.h.example").isTrue();
        assertThat(m.group(1)).as("valor de placeholder").matches("REPLACE[A-Z_]*");

        assertThat(literals(example).stream().filter(HardwareWokwiConsistencyTest::looksLikeToken).toList())
                .as("literales con forma de token en secrets.h.example").isEmpty();
    }

    @Test
    @DisplayName("hardware/wokwi/.gitignore excluye secrets.h (comprobar tambien con git check-ignore)")
    void gitignore_excludes_secrets_h() throws IOException {
        List<String> lines = Files.readAllLines(hardware().resolve(".gitignore"), StandardCharsets.UTF_8).stream()
                .map(String::trim)
                .toList();
        assertThat(lines).contains("secrets.h");
    }
}
