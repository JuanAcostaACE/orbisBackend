/*
 * SmartCane AI — ESP32 (Simulación Wokwi + Hardware físico)
 *
 * ARQUITECTURA NO-BLOQUEANTE:
 *   El loop() nunca se detiene por WiFi ni por HTTP.
 *   La seguridad local (vibrador) siempre responde aunque no haya Internet.
 *
 *   safetyTask (Core 0, FreeRTOS)  → sensor + vibrador + cola. SIN red.
 *   loop()         (Core 1)            → WiFi + HTTP con backoff.
 *
 * FILTRADO DE LECTURAS (HC-SR04):
 *   - Lectura válida  : 0 < distancia <= 400 cm.
 *   - Lectura inválida: distancia <= 0, sin eco (timeout) o > 400 cm.
 *     Una lectura inválida aislada se IGNORA (no cambia el estado de alerta).
 *     Si hay INVALID_READINGS_FOR_CLEAR seguidas se interpreta como
 *     "sin eco = camino libre" y se apaga el vibrador.
 *
 * HISTÉRESIS Y COOLDOWN (alineado con simulador alertas.ts y PassiveState.java):
 *   - Entra en PELIGRO  cuando distancia <= DANGER_CM (100 cm) → vibra al instante.
 *   - Sale  de PELIGRO  cuando distancia >  DANGER_CM + HYSTERESIS_CM (115 cm).
 *   - Se reporta UN evento por episodio de peligro y nunca antes de COOLDOWN_MS.
 *     Si el episodio empieza dentro del cooldown, el reporte queda pendiente y
 *     se envía en cuanto termina el cooldown (si el obstáculo sigue ahí).
 *
 * HTTP ROBUSTO:
 *   - El evento NO se descarta hasta confirmar la respuesta.
 *   - Fallo transitorio (sin red, 5xx, timeout, 408, 429): backoff exponencial
 *     BACKOFF_INITIAL_MS → BACKOFF_MAX_MS. No se martillea el backend.
 *   - Error de cliente (400, 401, 403, 404...): se DESCARTA el evento (reintentar
 *     no lo arreglaría) y se avisa por Serial para revisar token/payload.
 *
 * AUTENTICACIÓN (backend con Spring Security):
 *   El ESP32 envía el header X-Device-Token con el valor de DEVICE_TOKEN.
 *   El token debe almacenarse en NVS en hardware físico (no en el sketch).
 *   Para Wokwi (simulación), usar un secrets.h local (ver CONFIGURACIÓN abajo).
 *   El token NUNCA se imprime por Serial.
 *
 * SECRETOS:
 *   ⚠  No incluir credenciales WiFi reales ni el token de producción en este archivo.
 *   ⚠  Para hardware físico: cargar WiFi+Token vía NVS o archivo de configuración.
 *   ⚠  El token embebido en el firmware puede extraerse con herramientas de dumping.
 *   ⚠  Para HTTPS en hardware real usar WiFiClientSecure + setCACert() (no setInsecure()).
 *
 * COMPATIBILIDAD WOKWI:
 *   - HTTPClient, WiFi, ArduinoJson: compatibles con Wokwi.
 *   - FreeRTOS (xTaskCreate): compatible con Wokwi ESP32.
 *   - NVS: NO simulado en Wokwi → usar valores constantes en simulación.
 *   - Hardware real: reemplazar SSID/PASSWORD/DEVICE_TOKEN por lectura de NVS.
 *
 * CABLEADO (debe coincidir con diagram.json):
 *   esp32:D5  → HC-SR04 TRIG        esp32:D18 → HC-SR04 ECHO
 *   esp32:D4  → pushbutton (a GND)  esp32:D2  → LED rojo (motor vibrador)
 *   esp32:D13 → LED azul (estado WiFi)
 */

#include <WiFi.h>
#include <HTTPClient.h>
#include <ArduinoJson.h>

// Secretos locales: hardware/wokwi/secrets.h NO se versiona (ver .gitignore de esta carpeta).
// Copiar secrets.h.example a secrets.h y poner ahí el token nuevo. Sin secrets.h la
// compilación se detiene a propósito: el firmware no tiene ningún valor por defecto de token.
#if __has_include("secrets.h")
  #include "secrets.h"        // define SECRET_DEVICE_TOKEN
#else
  #error "Falta hardware/wokwi/secrets.h: copia secrets.h.example y define SECRET_DEVICE_TOKEN (no usar valores por defecto)"
#endif

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
  // Mientras no empiece por "http" el firmware NO intenta enviar (solo alerta local).
  const char* BACKEND_URL  = "REPLACE_WITH_BACKEND_URL";
  const char* ENDPOINT     = "/api/v1/events";

  // Token de dispositivo LEGADO compartido. Debe coincidir con APP_DEVICE_TOKEN en Railway.
  // Viene de secrets.h (no versionado). En hardware físico: leer de NVS.
  // Pendiente: sustituir por identidad por dispositivo (Bearer). No añadir aquí tokens literales.
  const char* DEVICE_TOKEN = SECRET_DEVICE_TOKEN;
}

// ══════════════════════════════════════════════════════════════════
//  PINES  (idénticos a diagram.json)
// ══════════════════════════════════════════════════════════════════
namespace Pin {
  const int TRIGGER      = 5;   // esp32:D5  → ultrasonic:TRIG
  const int ECHO         = 18;  // esp32:D18 → ultrasonic:ECHO
  const int BUTTON        = 4;   // esp32:D4  → button:1.l (INPUT_PULLUP, 2.l a GND)
  const int VIBRATOR     = 2;   // esp32:D2  → vibratorLed:A (LED rojo simula el motor)
  const int STATUS_LED   = 13;  // esp32:D13 → statusLed:A   (LED azul: WiFi conectado)
}

// ══════════════════════════════════════════════════════════════════
//  UMBRALES Y TIEMPOS (centralizado; documentado en cm y ms)
// ══════════════════════════════════════════════════════════════════
namespace Threshold {
  const float DANGER_CM     = 100.0f; // Peligro si distancia <= 100 cm
  const float HYSTERESIS_CM  =  15.0f; // Seguro solo si distancia > 115 cm
  const float MAX_VALID_CM  = 400.0f; // Lecturas > 400 cm son inválidas
  const float MIN_EXCLUSIVE_CM = 0.0f; // Lecturas <= 0 cm son inválidas
  const int   INVALID_READINGS_FOR_CLEAR = 3; // 3 inválidas seguidas = sin eco = libre
}

namespace Timing {
  const unsigned long TICK_SENSOR_MS     =  150;  // Frecuencia de lectura del sensor
  const unsigned long TICK_WIFI_MS       = 5000;  // Frecuencia de verificación WiFi
  const unsigned long TICK_HTTP_MS       =  500;  // Frecuencia de revisión de la cola HTTP
  const unsigned long COOLDOWN_MS        = 5000;  // Mínimo entre eventos (igual que el simulador)
  const unsigned long WIFI_TIMEOUT_MS    = 8000;  // Timeout de intento de conexión WiFi
  const unsigned long HTTP_TIMEOUT_MS    = 4000;  // Timeout de la petición HTTP
  const unsigned long DEBOUNCE_BTN_MS    = 2000;  // Debounce del botón físico
  const unsigned long BACKOFF_INITIAL_MS = 2000;  // Primer reintento HTTP tras fallo transitorio
  const unsigned long BACKOFF_MAX_MS     = 30000; // Tope del backoff exponencial
}

// ══════════════════════════════════════════════════════════════════
//  ESTADO INTERNO (volatile para acceso desde ISR/FreeRTOS)
// ══════════════════════════════════════════════════════════════════

// Evento pendiente de envío (lo escribe safetyTask, lo consume loop)
struct PendingEvent {
  volatile bool   pending;
  char            mode[8];
  float           distanceCm;
};

static PendingEvent queuedEvent  = { false, "PASSIVE", 0.0f };
static portMUX_TYPE    queueMux       = portMUX_INITIALIZER_UNLOCKED;

// Estado de seguridad (solo lo toca safetyTask, Core 0)
static bool          obstacleActive      = false;
static bool          reportPending     = false;  // episodio sin reportar (por cooldown)
static bool          hadEvent           = false;
static float         lastValidDistance = -1.0f;
static unsigned long lastEvent_ms      = 0;
static unsigned long lastButton_ms       = 0;

// Estado de red (solo lo toca loop, Core 1)
static unsigned long nextHttpAttempt_ms = 0;
static unsigned long currentBackoff_ms      = Timing::BACKOFF_INITIAL_MS;

// ══════════════════════════════════════════════════════════════════
//  PROTOTIPOS
// ══════════════════════════════════════════════════════════════════
float   measureDistanceCm();
bool    isValidReading(float distanceCm);
void    updateVibrator(bool activate);
void    releaseObstacle();
void    enqueueEvent(const char* mode, float distance);
int     sendEventHTTP(const char* mode, float distance);
bool    backendConfigured();
void    tryWiFiConnection();
void    safetyTask(void* param);

// ══════════════════════════════════════════════════════════════════
//  SETUP
// ══════════════════════════════════════════════════════════════════
void setup() {
  Serial.begin(115200);
  delay(100);

  pinMode(Pin::TRIGGER,    OUTPUT);
  pinMode(Pin::ECHO,       INPUT);
  pinMode(Pin::BUTTON,      INPUT_PULLUP);
  pinMode(Pin::VIBRATOR,   OUTPUT);
  pinMode(Pin::STATUS_LED, OUTPUT);

  digitalWrite(Pin::VIBRATOR,   LOW);
  digitalWrite(Pin::STATUS_LED, LOW);

  Serial.println(F("\n[SmartCane] Iniciando (arquitectura no-bloqueante)..."));
  if (!backendConfigured()) {
    Serial.println(F("[CFG] BACKEND_URL sin configurar: solo alerta local, no se enviarán eventos."));
  }

  // Tarea FreeRTOS en Core 0: solo seguridad local (sensor + vibrador)
  // No se detiene aunque Core 1 esté esperando WiFi o HTTP.
  xTaskCreatePinnedToCore(
    safetyTask,
    "Safety",
    4096,         // Stack en bytes
    nullptr,
    2,            // Prioridad alta (mayor que loop)
    nullptr,
    0             // Core 0
  );

  // Conexión WiFi inicial (con timeout; la seguridad local ya está corriendo)
  tryWiFiConnection();
}

// ══════════════════════════════════════════════════════════════════
//  LOOP PRINCIPAL (Core 1) — solo red, sin bloquear seguridad
// ══════════════════════════════════════════════════════════════════
void loop() {
  unsigned long now = millis();
  static unsigned long lastWifi = 0;
  static unsigned long lastHttp = 0;

  // ── Verificación periódica de WiFi ──
  if (now - lastWifi >= Timing::TICK_WIFI_MS) {
    lastWifi = now;
    if (WiFi.status() != WL_CONNECTED) {
      digitalWrite(Pin::STATUS_LED, LOW);
      Serial.println(F("[WiFi] Desconectado. Reintentando..."));
      tryWiFiConnection();
    }
  }

  // ── Envío HTTP: solo si hay URL, WiFi y no estamos en backoff ──
  if (now - lastHttp >= Timing::TICK_HTTP_MS) {
    lastHttp = now;

    bool canAttempt = backendConfigured() &&
                         WiFi.status() == WL_CONNECTED &&
                         (long)(now - nextHttpAttempt_ms) >= 0;

    if (canAttempt) {
      bool  hasEvent;
      char  localMode[8];
      float localDist = 0.0f;

      // Copia atómica: el evento permanece en la cola hasta confirmar la respuesta.
      // (enqueueEvent no sobrescribe mientras hay=true, así que el hueco es nuestro.)
      portENTER_CRITICAL(&queueMux);
      hasEvent = queuedEvent.pending;
      if (hasEvent) {
        strncpy(localMode, queuedEvent.mode, sizeof(localMode) - 1);
        localMode[sizeof(localMode) - 1] = '\0';
        localDist = queuedEvent.distanceCm;
      }
      portEXIT_CRITICAL(&queueMux);

      if (hasEvent) {
        int  code  = sendEventHTTP(localMode, localDist);
        bool success   = (code >= 200 && code < 300);
        bool clientError = (code >= 400 && code < 500 && code != 408 && code != 429);

        if (success || clientError) {
          // Éxito o rechazo definitivo: liberar la cola y reiniciar el backoff
          portENTER_CRITICAL(&queueMux);
          queuedEvent.pending = false;
          portEXIT_CRITICAL(&queueMux);
          currentBackoff_ms = Timing::BACKOFF_INITIAL_MS;
          if (clientError) {
            Serial.printf("[HTTP] Evento descartado (error de cliente %d). Revisar token/payload.\n", code);
          }
        } else {
          // Fallo transitorio: conservar el evento y esperar con backoff exponencial
          nextHttpAttempt_ms = millis() + currentBackoff_ms;
          Serial.printf("[HTTP] Fallo transitorio. Reintento en %lu ms\n", currentBackoff_ms);
          currentBackoff_ms = min(currentBackoff_ms * 2UL, Timing::BACKOFF_MAX_MS);
        }
      }
    }
  }
}

// ══════════════════════════════════════════════════════════════════
//  TAREA FREERTOS — SEGURIDAD LOCAL (Core 0)
//  NUNCA usa WiFi ni HTTP. Responde siempre.
// ══════════════════════════════════════════════════════════════════
void safetyTask(void* param) {
  unsigned long lastTick = 0;
  int consecutiveInvalid = 0;

  for (;;) {
    unsigned long now = millis();

    if (now - lastTick >= Timing::TICK_SENSOR_MS) {
      lastTick = now;

      float distance = measureDistanceCm();

      if (isValidReading(distance)) {
        consecutiveInvalid = 0;
        lastValidDistance = distance;

        // ── Histéresis: entrar ≤ 100 cm, salir > 115 cm ──
        if (distance <= Threshold::DANGER_CM) {
          if (!obstacleActive) {
            obstacleActive  = true;
            reportPending = true;
            updateVibrator(true);   // Alerta local INMEDIATA, sin red
            Serial.printf("[SEGURIDAD] Obstaculo: %.1f cm\n", distance);
          }
        } else if (obstacleActive &&
                   distance > (Threshold::DANGER_CM + Threshold::HYSTERESIS_CM)) {
          releaseObstacle();
        }
      } else {
        // Lectura inválida (<= 0, sin eco o > 400 cm): se ignora.
        // Varias seguidas = sin eco = camino libre.
        if (++consecutiveInvalid >= Threshold::INVALID_READINGS_FOR_CLEAR && obstacleActive) {
          releaseObstacle();
        }
      }

      // ── Reporte con cooldown: 1 evento por episodio, nunca antes de COOLDOWN_MS ──
      if (obstacleActive && reportPending &&
          (!hadEvent || now - lastEvent_ms >= Timing::COOLDOWN_MS)) {
        lastEvent_ms  = now;
        hadEvent       = true;
        reportPending = false;
        enqueueEvent("PASSIVE", lastValidDistance);
      }

      // ── Modo Activo: botón ──
      if (digitalRead(Pin::BUTTON) == LOW) {
        if (now - lastButton_ms >= Timing::DEBOUNCE_BTN_MS) {
          lastButton_ms = now;
          if (lastValidDistance > 0.0f) {
            Serial.printf("[ACTIVE] Boton. Distancia: %.1f cm\n", lastValidDistance);
            enqueueEvent("ACTIVE", lastValidDistance);
          } else {
            Serial.println(F("[ACTIVE] Boton ignorado: aun no hay lectura valida del sensor"));
          }
        }
      }
    }

    vTaskDelay(1); // Cede el control brevemente para el watchdog
  }
}

// ══════════════════════════════════════════════════════════════════
//  FUNCIONES
// ══════════════════════════════════════════════════════════════════

/** Devuelve la distancia en cm, o -1 si no hubo eco (timeout). */
float measureDistanceCm() {
  digitalWrite(Pin::TRIGGER, LOW);
  delayMicroseconds(2);
  digitalWrite(Pin::TRIGGER, HIGH);
  delayMicroseconds(10);
  digitalWrite(Pin::TRIGGER, LOW);

  long duration = pulseIn(Pin::ECHO, HIGH, 25000); // Timeout 25 ms (≈ 425 cm)
  if (duration == 0) return -1.0f;                 // Sin eco → lectura inválida
  return duration * 0.034f / 2.0f;
}

/** Válida si 0 < distancia <= 400 cm. */
bool isValidReading(float distanceCm) {
  return distanceCm > Threshold::MIN_EXCLUSIVE_CM && distanceCm <= Threshold::MAX_VALID_CM;
}

void updateVibrator(bool activate) {
  digitalWrite(Pin::VIBRATOR, activate ? HIGH : LOW);
}

void releaseObstacle() {
  obstacleActive  = false;
  reportPending = false;
  updateVibrator(false);
  Serial.println(F("[SEGURIDAD] Camino libre"));
}

/** Encola un evento para envío HTTP posterior (llamado desde safetyTask). */
void enqueueEvent(const char* mode, float distance) {
  portENTER_CRITICAL(&queueMux);
  // Solo encolar si no hay uno pendiente (evitar sobreescribir; el anterior tiene prioridad)
  if (!queuedEvent.pending) {
    queuedEvent.pending = true;
    strncpy(queuedEvent.mode, mode, sizeof(queuedEvent.mode) - 1);
    queuedEvent.mode[sizeof(queuedEvent.mode) - 1] = '\0';
    queuedEvent.distanceCm = distance;
  }
  portEXIT_CRITICAL(&queueMux);
}

/** True si BACKEND_URL ya fue reemplazada por una URL real. */
bool backendConfigured() {
  return strncmp(Cfg::BACKEND_URL, "http", 4) == 0;
}

/**
 * Envía el evento al backend.
 * Devuelve el código HTTP (200-299 éxito) o un valor <= 0 si falló la conexión.
 */
int sendEventHTTP(const char* mode, float distance) {
  String url = String(Cfg::BACKEND_URL) + String(Cfg::ENDPOINT);

  // Construir JSON (sin imageUrl: el ESP32-Maestro no envía imágenes)
  StaticJsonDocument<256> doc;
  doc["mode"]        = mode;
  doc["distanceCm"] = distance;
  // aiLabels omitida: el backend asigna null en modo PASSIVE
  String jsonBody;
  serializeJson(doc, jsonBody);

  HTTPClient http;
  http.setTimeout(Timing::HTTP_TIMEOUT_MS);
  http.begin(url);
  http.addHeader("Content-Type", "application/json");
  http.addHeader("Accept",       "application/json");
  // Autenticación por token de dispositivo (no usa sesión ni cookies).
  // El valor del token NUNCA se imprime.
  // TEMPORAL: X-Device-Token es un token LEGADO compartido por todos los dispositivos
  // (DeviceTokenFilter lo valida con APP_DEVICE_TOKEN). El siguiente paso es identidad
  // por dispositivo con Authorization: Bearer <token por dispositivo>, revocable de forma
  // individual. Hasta entonces, el token va en secrets.h (no versionado), nunca en el repo.
  http.addHeader("X-Device-Token", Cfg::DEVICE_TOKEN);

  Serial.printf("[HTTP] POST %s modo=%s dist=%.1f\n", url.c_str(), mode, distance);
  int code = http.POST(jsonBody);

  if (code >= 200 && code < 300) {
    Serial.printf("[HTTP] OK %d\n", code);
  } else if (code > 0) {
    Serial.printf("[HTTP] Error servidor/cliente %d\n", code);
  } else {
    Serial.printf("[HTTP] Error conexion: %s\n", http.errorToString(code).c_str());
  }

  http.end();
  return code;
}

/** Intento de conexión WiFi con timeout. No bloquea más de WIFI_TIMEOUT_MS. */
void tryWiFiConnection() {
  if (WiFi.status() == WL_CONNECTED) return;

  Serial.printf("[WiFi] Conectando a %s...\n", Cfg::SSID);
  WiFi.begin(Cfg::SSID, Cfg::PASSWORD);

  unsigned long start = millis();
  while (WiFi.status() != WL_CONNECTED &&
         millis() - start < Timing::WIFI_TIMEOUT_MS) {
    delay(200);
    Serial.print('.');
  }
  Serial.println();

  if (WiFi.status() == WL_CONNECTED) {
    Serial.printf("[WiFi] Conectado. IP: %s\n", WiFi.localIP().toString().c_str());
    digitalWrite(Pin::STATUS_LED, HIGH);
  } else {
    Serial.println(F("[WiFi] Sin conexion. El baston sigue funcionando localmente."));
    digitalWrite(Pin::STATUS_LED, LOW);
  }
}
