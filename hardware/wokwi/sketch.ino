/*
 * SmartCane AI — ESP32 Maestro (Simulación Wokwi)
 * Avance 1: Wokwi → JSON → Ngrok → Spring Boot → Strategy → Supabase
 *
 * INSTRUCCIONES:
 * 1. Antes de correr en Wokwi, reemplaza NGROK_URL con la URL real de Ngrok.
 *    Ejemplo: "https://abc123.ngrok-free.app"
 * 2. Las credenciales WiFi para Wokwi pueden ser cualquier cosa (usa el WiFi simulado).
 * 3. Para simular obstáculo: en Wokwi, haz clic en el sensor HC-SR04 y reduce la distancia.
 * 4. Para simular modo activo: presiona el botón verde en el diagrama.
 */

#include <WiFi.h>
#include <HTTPClient.h>
#include <ArduinoJson.h>

// ══════════════════════════════════════════
//  CONFIGURACIÓN — EDITAR ANTES DE CORRER
// ══════════════════════════════════════════
const char* SSID           = "Wokwi-GUEST";   // Red simulada de Wokwi (no cambiar en simulación)
const char* PASSWORD       = "";               // Sin contraseña en Wokwi
const char* NGROK_URL      = "REEMPLAZAR_CON_TU_URL_NGROK"; // ej: https://abc.ngrok-free.app
const char* ENDPOINT       = "/api/v1/eventos";

// ══════════════════════════════════════════
//  PINES
// ══════════════════════════════════════════
const int PIN_TRIGGER      = 5;
const int PIN_ECHO         = 18;
const int PIN_BOTON        = 4;
const int PIN_LED_VIBRADOR = 2;   // Simula motor vibrador (LED rojo)
const int PIN_LED_ESTADO   = 13;  // LED azul: estado WiFi/conexión

// ══════════════════════════════════════════
//  UMBRALES
// ══════════════════════════════════════════
const float UMBRAL_DISTANCIA_CM = 50.0;  // Obstáculo si < 50 cm
const int   DELAY_LECTURA_MS    = 300;   // Cada cuánto lee el sensor

// ══════════════════════════════════════════
//  ESTADO
// ══════════════════════════════════════════
bool modoActivoSolicitado = false;
bool obstaculoDetectado   = false;

// ══════════════════════════════════════════
//  SETUP
// ══════════════════════════════════════════
void setup() {
  Serial.begin(115200);

  pinMode(PIN_TRIGGER,      OUTPUT);
  pinMode(PIN_ECHO,         INPUT);
  pinMode(PIN_BOTON,        INPUT_PULLUP);
  pinMode(PIN_LED_VIBRADOR, OUTPUT);
  pinMode(PIN_LED_ESTADO,   OUTPUT);

  Serial.println("[SmartCane] Iniciando...");
  conectarWiFi();
}

// ══════════════════════════════════════════
//  LOOP PRINCIPAL
// ══════════════════════════════════════════
void loop() {
  float distancia = medirDistanciaCm();
  Serial.printf("[Sensor] Distancia: %.1f cm\n", distancia);

  // — Modo Pasivo: obstáculo detectado automáticamente
  if (distancia < UMBRAL_DISTANCIA_CM && distancia > 0) {
    if (!obstaculoDetectado) {
      obstaculoDetectado = true;
      activarVibrador(true);
      Serial.println("[PASIVO] Obstáculo detectado. Enviando telemetría...");
      enviarEvento("PASIVO", distancia, "");
    }
  } else {
    obstaculoDetectado = false;
    activarVibrador(false);
  }

  // — Modo Activo: usuario presionó el botón
  if (digitalRead(PIN_BOTON) == LOW && !modoActivoSolicitado) {
    modoActivoSolicitado = true;
    Serial.println("[ACTIVO] Botón presionado. Solicitando análisis de IA...");

    // En hardware real: aquí se enviaría señal Serial al ESP32-CAM
    // En simulación: enviamos con una imagen base64 de ejemplo vacía
    String imagenSimulada = ""; // En el Avance 2 viene del ESP32-CAM real
    enviarEvento("ACTIVO", distancia, imagenSimulada);

    delay(2000); // Debounce
    modoActivoSolicitado = false;
  }

  delay(DELAY_LECTURA_MS);
}

// ══════════════════════════════════════════
//  FUNCIONES
// ══════════════════════════════════════════

void conectarWiFi() {
  Serial.printf("[WiFi] Conectando a %s", SSID);
  WiFi.begin(SSID, PASSWORD);

  int intentos = 0;
  while (WiFi.status() != WL_CONNECTED && intentos < 20) {
    delay(500);
    Serial.print(".");
    intentos++;
  }

  if (WiFi.status() == WL_CONNECTED) {
    Serial.printf("\n[WiFi] Conectado. IP: %s\n", WiFi.localIP().toString().c_str());
    digitalWrite(PIN_LED_ESTADO, HIGH);
  } else {
    Serial.println("\n[WiFi] ERROR: No se pudo conectar.");
    digitalWrite(PIN_LED_ESTADO, LOW);
  }
}

float medirDistanciaCm() {
  digitalWrite(PIN_TRIGGER, LOW);
  delayMicroseconds(2);
  digitalWrite(PIN_TRIGGER, HIGH);
  delayMicroseconds(10);
  digitalWrite(PIN_TRIGGER, LOW);

  long duracion = pulseIn(PIN_ECHO, HIGH, 30000);
  if (duracion == 0) return 999.0; // Sin respuesta = sin obstáculo
  return duracion * 0.034 / 2.0;
}

void activarVibrador(bool activar) {
  digitalWrite(PIN_LED_VIBRADOR, activar ? HIGH : LOW);
}

void enviarEvento(String modo, float distanciaCm, String imagenBase64) {
  if (WiFi.status() != WL_CONNECTED) {
    Serial.println("[HTTP] Sin WiFi. Reintentando conexión...");
    conectarWiFi();
    return;
  }

  String url = String(NGROK_URL) + String(ENDPOINT);

  // Construir JSON
  StaticJsonDocument<1024> doc;
  doc["modo"]        = modo;
  doc["distanciaCm"] = distanciaCm;
  if (imagenBase64.length() > 0) {
    doc["imagenUrl"] = imagenBase64;
  }

  String jsonBody;
  serializeJson(doc, jsonBody);

  Serial.printf("[HTTP] POST %s\n", url.c_str());
  Serial.printf("[HTTP] Body: %s\n", jsonBody.c_str());

  HTTPClient http;
  http.begin(url);
  http.addHeader("Content-Type", "application/json");

  int codigoRespuesta = http.POST(jsonBody);

  if (codigoRespuesta > 0) {
    String respuesta = http.getString();
    Serial.printf("[HTTP] Respuesta %d: %s\n", codigoRespuesta, respuesta.c_str());
  } else {
    Serial.printf("[HTTP] ERROR: %s\n", http.errorToString(codigoRespuesta).c_str());
  }

  http.end();
}
