/*
 * SmartCane AI — ESP32 (Simulación Wokwi + Hardware físico)
 *
 * ARQUITECTURA NO-BLOQUEANTE:
 *   El loop() nunca se detiene por WiFi ni por HTTP.
 *   La seguridad local (vibrador) siempre responde aunque no haya Internet.
 *
 *   loop()
 *    ├─ tick cada TICK_SENSOR_MS → leer distancia → vibrar si obstáculo
 *    ├─ tick cada TICK_WIFI_MS   → verificar/reconectar WiFi
 *    └─ tick cada TICK_HTTP_MS   → enviar evento pendiente si hay WiFi
 *
 * HISTÉRESIS Y COOLDOWN:
 *   - Se reporta un evento al superar el umbral (flanco de bajada).
 *   - No se vuelve a reportar hasta que la distancia supere UMBRAL + HISTERESIS_CM.
 *   - Cooldown mínimo de COOLDOWN_MS entre eventos consecutivos.
 *
 * AUTENTICACIÓN (backend con Spring Security):
 *   El ESP32 envía el header X-Device-Token con el valor de DEVICE_TOKEN.
 *   El token debe almacenarse en NVS en hardware físico (no en el sketch).
 *   Para Wokwi (simulación), se puede dejar un valor de prueba.
 *
 * SECRETOS:
 *   ⚠  No incluir credenciales WiFi reales ni el token de producción en este archivo.
 *   ⚠  Para hardware físico: cargar WiFi+Token vía NVS o archivo de configuración.
 *   ⚠  El token embebido en el firmware puede extraerse con herramientas de dumping.
 *
 * COMPATIBILIDAD WOKWI:
 *   - HTTPClient, WiFi, ArduinoJson: compatibles con Wokwi.
 *   - FreeRTOS (xTaskCreate): compatible con Wokwi ESP32.
 *   - NVS: NO simulado en Wokwi → usar valores constantes en simulación.
 *   - Hardware real: reemplazar SSID/PASSWORD/DEVICE_TOKEN por lectura de NVS.
 */

#include <WiFi.h>
#include <HTTPClient.h>
#include <ArduinoJson.h>

// ══════════════════════════════════════════════════════════════════
//  CONFIGURACIÓN — EDITAR ANTES DE CORRER
//  ⚠ Para producción: mover a NVS o a un archivo no versionado.
// ══════════════════════════════════════════════════════════════════
namespace Cfg {
  // Red WiFi (Wokwi usa "Wokwi-GUEST" sin contraseña)
  const char* SSID         = "Wokwi-GUEST";
  const char* PASSWORD     = "";

  // URL base del backend (incluye esquema, sin slash final)
  // Ejemplo ngrok:   "https://abc123.ngrok-free.app"
  // Ejemplo Railway: "https://orbisbackend-production.up.railway.app"
  const char* BACKEND_URL  = "REEMPLAZAR_CON_URL_DEL_BACKEND";
  const char* ENDPOINT     = "/api/v1/eventos";

  // Token de dispositivo. Debe coincidir con APP_DEVICE_TOKEN en Railway.
  // ⚠ En hardware físico: leer de NVS, nunca dejar el valor real en el sketch.
  const char* DEVICE_TOKEN = "TOKEN_DE_PRUEBA_CAMBIAR_EN_PRODUCCION";
}

// ══════════════════════════════════════════════════════════════════
//  PINES
// ══════════════════════════════════════════════════════════════════
namespace Pin {
  const int TRIGGER      = 5;
  const int ECHO         = 18;
  const int BOTON        = 4;
  const int VIBRADOR     = 2;   // LED rojo simula motor vibrador
  const int LED_ESTADO   = 13;  // LED azul: conectado a WiFi
}

// ══════════════════════════════════════════════════════════════════
//  UMBRALES Y TIEMPOS (centralizado; documentado en cm y ms)
// ══════════════════════════════════════════════════════════════════
namespace Umbral {
  const float DISTANCIA_CM   = 50.0f;  // Obstáculo detectado si distancia < 50 cm
  const float HISTERESIS_CM  = 10.0f;  // Debe superar 60 cm para "limpiar" la detección
  const float MAX_VALIDA_CM  = 400.0f; // Lecturas > 400 cm se tratan como "sin obstáculo"
  const float MIN_VALIDA_CM  = 2.0f;   // Lecturas < 2 cm se tratan como ruido
}

namespace Tiempo {
  const unsigned long TICK_SENSOR_MS  =  150;  // Frecuencia de lectura del sensor
  const unsigned long TICK_WIFI_MS    = 5000;  // Frecuencia de verificación WiFi
  const unsigned long TICK_HTTP_MS    =  500;  // Frecuencia de envío HTTP
  const unsigned long COOLDOWN_MS     = 2000;  // Mínimo entre eventos consecutivos
  const unsigned long WIFI_TIMEOUT_MS = 8000;  // Timeout de intento de conexión WiFi
  const unsigned long HTTP_TIMEOUT_MS = 4000;  // Timeout de la petición HTTP
  const unsigned long DEBOUNCE_BTN_MS = 2000;  // Debounce del botón físico
}

// ══════════════════════════════════════════════════════════════════
//  ESTADO INTERNO (volatile para acceso desde ISR/FreeRTOS)
// ══════════════════════════════════════════════════════════════════

// Evento pendiente de envío (acceso desde loop, escrito por tareaSeguridad)
struct EventoPendiente {
  volatile bool   hay;
  char            modo[8];
  float           distanciaCm;
};

static EventoPendiente eventoEnCola  = { false, "PASIVO", 0.0f };
static portMUX_TYPE    colaMux       = portMUX_INITIALIZER_UNLOCKED;

static bool          obstaculoActivo = false;
static unsigned long ultimoEvento_ms = 0;
static unsigned long ultimoBoton_ms  = 0;

// ══════════════════════════════════════════════════════════════════
//  PROTOTIPOS
// ══════════════════════════════════════════════════════════════════
float   medirDistanciaCm();
void    actualizarVibrador(bool activar);
void    encolarEvento(const char* modo, float distancia);
bool    enviarEventoHTTP(const char* modo, float distancia);
void    intentarConexionWiFi();
void    tareaSeguridad(void* param);

// ══════════════════════════════════════════════════════════════════
//  SETUP
// ══════════════════════════════════════════════════════════════════
void setup() {
  Serial.begin(115200);
  delay(100);

  pinMode(Pin::TRIGGER,    OUTPUT);
  pinMode(Pin::ECHO,       INPUT);
  pinMode(Pin::BOTON,      INPUT_PULLUP);
  pinMode(Pin::VIBRADOR,   OUTPUT);
  pinMode(Pin::LED_ESTADO, OUTPUT);

  digitalWrite(Pin::VIBRADOR,   LOW);
  digitalWrite(Pin::LED_ESTADO, LOW);

  Serial.println(F("\n[SmartCane] Iniciando (arquitectura no-bloqueante)..."));

  // Tarea FreeRTOS en Core 0: solo seguridad local (sensor + vibrador)
  // No se detiene aunque Core 1 esté esperando WiFi o HTTP.
  xTaskCreatePinnedToCore(
    tareaSeguridad,
    "Seguridad",
    4096,         // Stack en bytes
    nullptr,
    2,            // Prioridad alta (mayor que loop)
    nullptr,
    0             // Core 0
  );

  // Conexión WiFi inicial (no bloqueante: usa timeout)
  intentarConexionWiFi();
}

// ══════════════════════════════════════════════════════════════════
//  LOOP PRINCIPAL (Core 1) — solo red, sin bloquear seguridad
// ══════════════════════════════════════════════════════════════════
void loop() {
  unsigned long ahora = millis();
  static unsigned long ultimoWifi = 0;
  static unsigned long ultimoHttp = 0;

  // ── Verificación periódica de WiFi ──
  if (ahora - ultimoWifi >= Tiempo::TICK_WIFI_MS) {
    ultimoWifi = ahora;
    if (WiFi.status() != WL_CONNECTED) {
      digitalWrite(Pin::LED_ESTADO, LOW);
      Serial.println(F("[WiFi] Desconectado. Reintentando..."));
      intentarConexionWiFi();
    }
  }

  // ── Envío HTTP periódico si hay evento pendiente ──
  if (ahora - ultimoHttp >= Tiempo::TICK_HTTP_MS) {
    ultimoHttp = ahora;

    bool hayEvento;
    char   modoLocal[8];
    float  distLocal;

    // Leer la cola de forma atómica
    portENTER_CRITICAL(&colaMux);
    hayEvento = eventoEnCola.hay;
    if (hayEvento) {
      strncpy(modoLocal, eventoEnCola.modo, sizeof(modoLocal) - 1);
      modoLocal[sizeof(modoLocal) - 1] = '\0';
      distLocal = eventoEnCola.distanciaCm;
      eventoEnCola.hay = false; // Marcar como consumido (optimista)
    }
    portEXIT_CRITICAL(&colaMux);

    if (hayEvento && WiFi.status() == WL_CONNECTED) {
      bool enviado = enviarEventoHTTP(modoLocal, distLocal);
      if (!enviado) {
        // Re-encolar si el envío falló (solo si la cola está vacía para no duplicar)
        portENTER_CRITICAL(&colaMux);
        if (!eventoEnCola.hay) {
          eventoEnCola.hay = true;
          strncpy(eventoEnCola.modo, modoLocal, sizeof(eventoEnCola.modo) - 1);
          eventoEnCola.distanciaCm = distLocal;
        }
        portEXIT_CRITICAL(&colaMux);
        Serial.println(F("[HTTP] Reintentará en el siguiente tick"));
      }
    } else if (hayEvento) {
      Serial.println(F("[HTTP] Sin WiFi. Evento guardado para próximo intento"));
      // Re-encolar
      portENTER_CRITICAL(&colaMux);
      eventoEnCola.hay = true;
      strncpy(eventoEnCola.modo, modoLocal, sizeof(eventoEnCola.modo) - 1);
      eventoEnCola.distanciaCm = distLocal;
      portEXIT_CRITICAL(&colaMux);
    }
  }
}

// ══════════════════════════════════════════════════════════════════
//  TAREA FREERTOS — SEGURIDAD LOCAL (Core 0)
//  NUNCA usa WiFi ni HTTP. Responde siempre.
// ══════════════════════════════════════════════════════════════════
void tareaSeguridad(void* param) {
  unsigned long ultimoTick = 0;

  for (;;) {
    unsigned long ahora = millis();

    if (ahora - ultimoTick >= Tiempo::TICK_SENSOR_MS) {
      ultimoTick = ahora;

      float distancia = medirDistanciaCm();

      // ── Lógica de histéresis ──
      bool dentroDeRango = (distancia >= Umbral::MIN_VALIDA_CM &&
                            distancia <= Umbral::MAX_VALIDA_CM);

      if (dentroDeRango && distancia < Umbral::DISTANCIA_CM) {
        // Dentro del umbral de peligro
        if (!obstaculoActivo) {
          obstaculoActivo = true;
          actualizarVibrador(true);
          Serial.printf("[SEGURIDAD] Obstáculo: %.1f cm\n", distancia);

          // Cooldown entre eventos
          if (ahora - ultimoEvento_ms >= Tiempo::COOLDOWN_MS) {
            ultimoEvento_ms = ahora;
            encolarEvento("PASIVO", distancia);
          }
        }
      } else if (obstaculoActivo &&
                 distancia > (Umbral::DISTANCIA_CM + Umbral::HISTERESIS_CM)) {
        // Superó umbral + histéresis: camino libre
        obstaculoActivo = false;
        actualizarVibrador(false);
        Serial.println(F("[SEGURIDAD] Camino libre"));
      }

      // ── Modo Activo: botón ──
      if (digitalRead(Pin::BOTON) == LOW) {
        if (ahora - ultimoBoton_ms >= Tiempo::DEBOUNCE_BTN_MS) {
          ultimoBoton_ms = ahora;
          Serial.printf("[ACTIVO] Botón. Distancia: %.1f cm\n", distancia);
          encolarEvento("ACTIVO", distancia);
        }
      }
    }

    vTaskDelay(1); // Cede el control brevemente para el watchdog
  }
}

// ══════════════════════════════════════════════════════════════════
//  FUNCIONES
// ══════════════════════════════════════════════════════════════════

float medirDistanciaCm() {
  digitalWrite(Pin::TRIGGER, LOW);
  delayMicroseconds(2);
  digitalWrite(Pin::TRIGGER, HIGH);
  delayMicroseconds(10);
  digitalWrite(Pin::TRIGGER, LOW);

  long duracion = pulseIn(Pin::ECHO, HIGH, 25000); // Timeout 25 ms (≈ 425 cm)
  if (duracion == 0) return Umbral::MAX_VALIDA_CM + 1.0f; // Sin respuesta = sin obstáculo
  return duracion * 0.034f / 2.0f;
}

void actualizarVibrador(bool activar) {
  digitalWrite(Pin::VIBRADOR, activar ? HIGH : LOW);
}

/** Encola un evento para envío HTTP posterior (llamado desde tareaSeguridad). */
void encolarEvento(const char* modo, float distancia) {
  portENTER_CRITICAL(&colaMux);
  // Solo encolar si no hay uno pendiente (evitar sobreescribir; el anterior tiene prioridad)
  if (!eventoEnCola.hay) {
    eventoEnCola.hay = true;
    strncpy(eventoEnCola.modo, modo, sizeof(eventoEnCola.modo) - 1);
    eventoEnCola.modo[sizeof(eventoEnCola.modo) - 1] = '\0';
    eventoEnCola.distanciaCm = distancia;
  }
  portEXIT_CRITICAL(&colaMux);
}

/** Envía el evento al backend. Devuelve true si HTTP 200-299. */
bool enviarEventoHTTP(const char* modo, float distancia) {
  String url = String(Cfg::BACKEND_URL) + String(Cfg::ENDPOINT);

  // Construir JSON (sin imagenUrl: el ESP32-Maestro no envía imágenes)
  StaticJsonDocument<256> doc;
  doc["modo"]        = modo;
  doc["distanciaCm"] = distancia;
  // etiquetasIA omitida: el backend asigna null en modo PASIVO
  String jsonBody;
  serializeJson(doc, jsonBody);

  HTTPClient http;
  http.setTimeout(Tiempo::HTTP_TIMEOUT_MS);
  http.begin(url);
  http.addHeader("Content-Type", "application/json");
  http.addHeader("Accept",       "application/json");
  // Autenticación por token de dispositivo (no usa sesión ni cookies)
  http.addHeader("X-Device-Token", Cfg::DEVICE_TOKEN);

  Serial.printf("[HTTP] POST %s modo=%s dist=%.1f\n", url.c_str(), modo, distancia);
  int codigo = http.POST(jsonBody);

  bool exito = (codigo >= 200 && codigo < 300);
  if (exito) {
    Serial.printf("[HTTP] OK %d\n", codigo);
  } else if (codigo > 0) {
    Serial.printf("[HTTP] Error servidor %d\n", codigo);
  } else {
    Serial.printf("[HTTP] Error conexion: %s\n", http.errorToString(codigo).c_str());
  }

  http.end();
  return exito;
}

/** Intento de conexión WiFi con timeout. No bloquea más de WIFI_TIMEOUT_MS. */
void intentarConexionWiFi() {
  if (WiFi.status() == WL_CONNECTED) return;

  Serial.printf("[WiFi] Conectando a %s...\n", Cfg::SSID);
  WiFi.begin(Cfg::SSID, Cfg::PASSWORD);

  unsigned long inicio = millis();
  while (WiFi.status() != WL_CONNECTED &&
         millis() - inicio < Tiempo::WIFI_TIMEOUT_MS) {
    delay(200);
    Serial.print('.');
  }
  Serial.println();

  if (WiFi.status() == WL_CONNECTED) {
    Serial.printf("[WiFi] Conectado. IP: %s\n", WiFi.localIP().toString().c_str());
    digitalWrite(Pin::LED_ESTADO, HIGH);
  } else {
    Serial.println(F("[WiFi] Sin conexión. El bastón sigue funcionando localmente."));
    digitalWrite(Pin::LED_ESTADO, LOW);
  }
}
