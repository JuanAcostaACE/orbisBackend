/*
 * SmartCane AI — ESP32 (Simulación Wokwi + Hardware físico)
 *
 * ARQUITECTURA NO-BLOQUEANTE:
 *   El loop() nunca se detiene por WiFi ni por HTTP.
 *   La seguridad local (vibrador) siempre responde aunque no haya Internet.
 *
 *   tareaSeguridad (Core 0, FreeRTOS)  → sensor + vibrador + cola. SIN red.
 *   loop()         (Core 1)            → WiFi + HTTP con backoff.
 *
 * FILTRADO DE LECTURAS (HC-SR04):
 *   - Lectura válida  : 0 < distancia <= 400 cm.
 *   - Lectura inválida: distancia <= 0, sin eco (timeout) o > 400 cm.
 *     Una lectura inválida aislada se IGNORA (no cambia el estado de alerta).
 *     Si hay LECTURAS_INVALIDAS_PARA_LIBRE seguidas se interpreta como
 *     "sin eco = camino libre" y se apaga el vibrador.
 *
 * HISTÉRESIS Y COOLDOWN (alineado con simulador alertas.ts y EstadoPasivo.java):
 *   - Entra en PELIGRO  cuando distancia <= PELIGRO_CM (100 cm) → vibra al instante.
 *   - Sale  de PELIGRO  cuando distancia >  PELIGRO_CM + HISTERESIS_CM (115 cm).
 *   - Se reporta UN evento por episodio de peligro y nunca antes de COOLDOWN_MS.
 *     Si el episodio empieza dentro del cooldown, el reporte queda pendiente y
 *     se envía en cuanto termina el cooldown (si el obstáculo sigue ahí).
 *
 * HTTP ROBUSTO:
 *   - El evento NO se descarta hasta confirmar la respuesta.
 *   - Fallo transitorio (sin red, 5xx, timeout, 408, 429): backoff exponencial
 *     BACKOFF_INICIAL_MS → BACKOFF_MAX_MS. No se martillea el backend.
 *   - Error de cliente (400, 401, 403, 404...): se DESCARTA el evento (reintentar
 *     no lo arreglaría) y se avisa por Serial para revisar token/payload.
 *
 * AUTENTICACIÓN (backend con Spring Security):
 *   El ESP32 envía el header X-Device-Token con el valor de DEVICE_TOKEN.
 *   El token debe almacenarse en NVS en hardware físico (no en el sketch).
 *   Para Wokwi (simulación), se puede dejar un valor de prueba.
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
  const char* BACKEND_URL  = "REEMPLAZAR_CON_URL_DEL_BACKEND";
  const char* ENDPOINT     = "/api/v1/eventos";

  // Token de dispositivo. Debe coincidir con APP_DEVICE_TOKEN en Railway.
  // ⚠ En hardware físico: leer de NVS, nunca dejar el valor real en el sketch.
  const char* DEVICE_TOKEN = "TOKEN_DE_PRUEBA_CAMBIAR_EN_PRODUCCION";
}

// ══════════════════════════════════════════════════════════════════
//  PINES  (idénticos a diagram.json)
// ══════════════════════════════════════════════════════════════════
namespace Pin {
  const int TRIGGER      = 5;   // esp32:D5  → ultrasonico:TRIG
  const int ECHO         = 18;  // esp32:D18 → ultrasonico:ECHO
  const int BOTON        = 4;   // esp32:D4  → boton:1.l (INPUT_PULLUP, 2.l a GND)
  const int VIBRADOR     = 2;   // esp32:D2  → ledVibrador:A (LED rojo simula el motor)
  const int LED_ESTADO   = 13;  // esp32:D13 → ledEstado:A   (LED azul: WiFi conectado)
}

// ══════════════════════════════════════════════════════════════════
//  UMBRALES Y TIEMPOS (centralizado; documentado en cm y ms)
// ══════════════════════════════════════════════════════════════════
namespace Umbral {
  const float PELIGRO_CM     = 100.0f; // Peligro si distancia <= 100 cm
  const float HISTERESIS_CM  =  15.0f; // Seguro solo si distancia > 115 cm
  const float MAX_VALIDA_CM  = 400.0f; // Lecturas > 400 cm son inválidas
  const float MIN_EXCLUSIVO_CM = 0.0f; // Lecturas <= 0 cm son inválidas
  const int   LECTURAS_INVALIDAS_PARA_LIBRE = 3; // 3 inválidas seguidas = sin eco = libre
}

namespace Tiempo {
  const unsigned long TICK_SENSOR_MS     =  150;  // Frecuencia de lectura del sensor
  const unsigned long TICK_WIFI_MS       = 5000;  // Frecuencia de verificación WiFi
  const unsigned long TICK_HTTP_MS       =  500;  // Frecuencia de revisión de la cola HTTP
  const unsigned long COOLDOWN_MS        = 5000;  // Mínimo entre eventos (igual que el simulador)
  const unsigned long WIFI_TIMEOUT_MS    = 8000;  // Timeout de intento de conexión WiFi
  const unsigned long HTTP_TIMEOUT_MS    = 4000;  // Timeout de la petición HTTP
  const unsigned long DEBOUNCE_BTN_MS    = 2000;  // Debounce del botón físico
  const unsigned long BACKOFF_INICIAL_MS = 2000;  // Primer reintento HTTP tras fallo transitorio
  const unsigned long BACKOFF_MAX_MS     = 30000; // Tope del backoff exponencial
}

// ══════════════════════════════════════════════════════════════════
//  ESTADO INTERNO (volatile para acceso desde ISR/FreeRTOS)
// ══════════════════════════════════════════════════════════════════

// Evento pendiente de envío (lo escribe tareaSeguridad, lo consume loop)
struct EventoPendiente {
  volatile bool   hay;
  char            modo[8];
  float           distanciaCm;
};

static EventoPendiente eventoEnCola  = { false, "PASIVO", 0.0f };
static portMUX_TYPE    colaMux       = portMUX_INITIALIZER_UNLOCKED;

// Estado de seguridad (solo lo toca tareaSeguridad, Core 0)
static bool          obstaculoActivo      = false;
static bool          reportePendiente     = false;  // episodio sin reportar (por cooldown)
static bool          huboEvento           = false;
static float         ultimaDistanciaValida = -1.0f;
static unsigned long ultimoEvento_ms      = 0;
static unsigned long ultimoBoton_ms       = 0;

// Estado de red (solo lo toca loop, Core 1)
static unsigned long proximoIntentoHttp_ms = 0;
static unsigned long backoffActual_ms      = Tiempo::BACKOFF_INICIAL_MS;

// ══════════════════════════════════════════════════════════════════
//  PROTOTIPOS
// ══════════════════════════════════════════════════════════════════
float   medirDistanciaCm();
bool    lecturaValida(float distanciaCm);
void    actualizarVibrador(bool activar);
void    liberarObstaculo();
void    encolarEvento(const char* modo, float distancia);
int     enviarEventoHTTP(const char* modo, float distancia);
bool    backendConfigurado();
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
  if (!backendConfigurado()) {
    Serial.println(F("[CFG] BACKEND_URL sin configurar: solo alerta local, no se enviarán eventos."));
  }

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

  // Conexión WiFi inicial (con timeout; la seguridad local ya está corriendo)
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

  // ── Envío HTTP: solo si hay URL, WiFi y no estamos en backoff ──
  if (ahora - ultimoHttp >= Tiempo::TICK_HTTP_MS) {
    ultimoHttp = ahora;

    bool puedeIntentar = backendConfigurado() &&
                         WiFi.status() == WL_CONNECTED &&
                         (long)(ahora - proximoIntentoHttp_ms) >= 0;

    if (puedeIntentar) {
      bool  hayEvento;
      char  modoLocal[8];
      float distLocal = 0.0f;

      // Copia atómica: el evento permanece en la cola hasta confirmar la respuesta.
      // (encolarEvento no sobrescribe mientras hay=true, así que el hueco es nuestro.)
      portENTER_CRITICAL(&colaMux);
      hayEvento = eventoEnCola.hay;
      if (hayEvento) {
        strncpy(modoLocal, eventoEnCola.modo, sizeof(modoLocal) - 1);
        modoLocal[sizeof(modoLocal) - 1] = '\0';
        distLocal = eventoEnCola.distanciaCm;
      }
      portEXIT_CRITICAL(&colaMux);

      if (hayEvento) {
        int  codigo  = enviarEventoHTTP(modoLocal, distLocal);
        bool exito   = (codigo >= 200 && codigo < 300);
        bool errorCliente = (codigo >= 400 && codigo < 500 && codigo != 408 && codigo != 429);

        if (exito || errorCliente) {
          // Éxito o rechazo definitivo: liberar la cola y reiniciar el backoff
          portENTER_CRITICAL(&colaMux);
          eventoEnCola.hay = false;
          portEXIT_CRITICAL(&colaMux);
          backoffActual_ms = Tiempo::BACKOFF_INICIAL_MS;
          if (errorCliente) {
            Serial.printf("[HTTP] Evento descartado (error de cliente %d). Revisar token/payload.\n", codigo);
          }
        } else {
          // Fallo transitorio: conservar el evento y esperar con backoff exponencial
          proximoIntentoHttp_ms = millis() + backoffActual_ms;
          Serial.printf("[HTTP] Fallo transitorio. Reintento en %lu ms\n", backoffActual_ms);
          backoffActual_ms = min(backoffActual_ms * 2UL, Tiempo::BACKOFF_MAX_MS);
        }
      }
    }
  }
}

// ══════════════════════════════════════════════════════════════════
//  TAREA FREERTOS — SEGURIDAD LOCAL (Core 0)
//  NUNCA usa WiFi ni HTTP. Responde siempre.
// ══════════════════════════════════════════════════════════════════
void tareaSeguridad(void* param) {
  unsigned long ultimoTick = 0;
  int invalidasSeguidas = 0;

  for (;;) {
    unsigned long ahora = millis();

    if (ahora - ultimoTick >= Tiempo::TICK_SENSOR_MS) {
      ultimoTick = ahora;

      float distancia = medirDistanciaCm();

      if (lecturaValida(distancia)) {
        invalidasSeguidas = 0;
        ultimaDistanciaValida = distancia;

        // ── Histéresis: entrar ≤ 100 cm, salir > 115 cm ──
        if (distancia <= Umbral::PELIGRO_CM) {
          if (!obstaculoActivo) {
            obstaculoActivo  = true;
            reportePendiente = true;
            actualizarVibrador(true);   // Alerta local INMEDIATA, sin red
            Serial.printf("[SEGURIDAD] Obstaculo: %.1f cm\n", distancia);
          }
        } else if (obstaculoActivo &&
                   distancia > (Umbral::PELIGRO_CM + Umbral::HISTERESIS_CM)) {
          liberarObstaculo();
        }
      } else {
        // Lectura inválida (<= 0, sin eco o > 400 cm): se ignora.
        // Varias seguidas = sin eco = camino libre.
        if (++invalidasSeguidas >= Umbral::LECTURAS_INVALIDAS_PARA_LIBRE && obstaculoActivo) {
          liberarObstaculo();
        }
      }

      // ── Reporte con cooldown: 1 evento por episodio, nunca antes de COOLDOWN_MS ──
      if (obstaculoActivo && reportePendiente &&
          (!huboEvento || ahora - ultimoEvento_ms >= Tiempo::COOLDOWN_MS)) {
        ultimoEvento_ms  = ahora;
        huboEvento       = true;
        reportePendiente = false;
        encolarEvento("PASIVO", ultimaDistanciaValida);
      }

      // ── Modo Activo: botón ──
      if (digitalRead(Pin::BOTON) == LOW) {
        if (ahora - ultimoBoton_ms >= Tiempo::DEBOUNCE_BTN_MS) {
          ultimoBoton_ms = ahora;
          if (ultimaDistanciaValida > 0.0f) {
            Serial.printf("[ACTIVO] Boton. Distancia: %.1f cm\n", ultimaDistanciaValida);
            encolarEvento("ACTIVO", ultimaDistanciaValida);
          } else {
            Serial.println(F("[ACTIVO] Boton ignorado: aun no hay lectura valida del sensor"));
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
float medirDistanciaCm() {
  digitalWrite(Pin::TRIGGER, LOW);
  delayMicroseconds(2);
  digitalWrite(Pin::TRIGGER, HIGH);
  delayMicroseconds(10);
  digitalWrite(Pin::TRIGGER, LOW);

  long duracion = pulseIn(Pin::ECHO, HIGH, 25000); // Timeout 25 ms (≈ 425 cm)
  if (duracion == 0) return -1.0f;                 // Sin eco → lectura inválida
  return duracion * 0.034f / 2.0f;
}

/** Válida si 0 < distancia <= 400 cm. */
bool lecturaValida(float distanciaCm) {
  return distanciaCm > Umbral::MIN_EXCLUSIVO_CM && distanciaCm <= Umbral::MAX_VALIDA_CM;
}

void actualizarVibrador(bool activar) {
  digitalWrite(Pin::VIBRADOR, activar ? HIGH : LOW);
}

void liberarObstaculo() {
  obstaculoActivo  = false;
  reportePendiente = false;
  actualizarVibrador(false);
  Serial.println(F("[SEGURIDAD] Camino libre"));
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

/** True si BACKEND_URL ya fue reemplazada por una URL real. */
bool backendConfigurado() {
  return strncmp(Cfg::BACKEND_URL, "http", 4) == 0;
}

/**
 * Envía el evento al backend.
 * Devuelve el código HTTP (200-299 éxito) o un valor <= 0 si falló la conexión.
 */
int enviarEventoHTTP(const char* modo, float distancia) {
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
  // Autenticación por token de dispositivo (no usa sesión ni cookies).
  // El valor del token NUNCA se imprime.
  http.addHeader("X-Device-Token", Cfg::DEVICE_TOKEN);

  Serial.printf("[HTTP] POST %s modo=%s dist=%.1f\n", url.c_str(), modo, distancia);
  int codigo = http.POST(jsonBody);

  if (codigo >= 200 && codigo < 300) {
    Serial.printf("[HTTP] OK %d\n", codigo);
  } else if (codigo > 0) {
    Serial.printf("[HTTP] Error servidor/cliente %d\n", codigo);
  } else {
    Serial.printf("[HTTP] Error conexion: %s\n", http.errorToString(codigo).c_str());
  }

  http.end();
  return codigo;
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
    Serial.println(F("[WiFi] Sin conexion. El baston sigue funcionando localmente."));
    digitalWrite(Pin::LED_ESTADO, LOW);
  }
}
