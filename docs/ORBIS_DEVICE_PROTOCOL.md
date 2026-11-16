# ORBIS — Protocolo ESP32-CAM ↔ Backend

Estado: **propuesta de diseño (v0.1)**. Nada de esto está implementado todavía en backend ni firmware.
Lo que existe hoy y lo que cambia está en la sección 9.

Principios que no se cambian aquí:
- La ESP32-CAM **no** se conecta a la app móvil. Ambos hablan solo con el backend.
- Bluetooth solo se usa entre teléfono y audífonos.
- Una captura = una fotografía solicitada por el botón. Nada de vídeo ni capturas periódicas.
- Una sola instancia de Railway durante bastante tiempo: sin colas externas, sin Redis, sin Kafka.

---

## 1. Identidad del dispositivo

Cada bastón tiene identidad propia. No existe credencial global para todos los dispositivos.

| Elemento | Dónde vive | Notas |
|---|---|---|
| `device_id` | Backend (UUID v4), asignado al registrar | Identificador público, puede aparecer en la app del auxiliar. |
| `device_token` | ESP32 (NVS, cifrado si el módulo lo permite) y **nunca** en el backend en claro | 256 bits aleatorios. Se devuelve una sola vez al registrar. |
| Hash del token | Backend (tabla `dispositivos`) | SHA-256 del token con sal de servidor. Se compara en tiempo constante. |
| Propietario | Backend | Un dispositivo pertenece a un usuario principal. El auxiliar no puede registrar dispositivos. |

**Asociación (vinculación explícita):**
1. El usuario principal pulsa "Añadir bastón" en la app. El backend genera un **código de vinculación** de 8 caracteres, de un solo uso, caduca en 10 minutos.
2. La ESP32 llama a `POST /api/v1/dispositivos/registro` con ese código.
3. El backend crea el dispositivo, guarda el hash del token y devuelve `device_token` una vez.
4. El código queda consumido aunque el registro falle después de validarlo (evita reutilización).

**Revocación:** el usuario revoca el dispositivo desde la app o el panel. El hash se marca como revocado y la siguiente petición responde `401`. No hay caché de autenticación que retrase la revocación.

**Rotación:** `POST /api/v1/dispositivos/token/rotar` (autenticado con el token actual) emite un token nuevo y invalida el anterior en la misma transacción. La rotación debe ocurrir antes de la caducidad configurada (propuesta: 180 días) y también después de un reinicio de fábrica.

**Protección frente a repetición:**
- El token va solo en cabecera `Authorization: Bearer`, nunca en la URL.
- HTTPS obligatorio. El firmware debe validar el certificado (`WiFiClientSecure` + `setCACert()`), no usar `setInsecure()`.
- La repetición de una captura queda neutralizada por la idempotencia de la sección 3.
- El reloj de la ESP32 no es fiable sin NTP. El backend usa su propio tiempo de recepción; el `captured_uptime_ms` del dispositivo es solo informativo.

**Migración desde el esquema actual:** hoy existe `X-Device-Token` con un único valor compartido que se asocia a un correo (`DeviceTokenFilter`, `DeviceConfigValidator`). Ese esquema es el que este documento sustituye. Mientras no se migre, debe considerarse un riesgo abierto (ver `docs/ORBIS_SECURITY_AND_PRIVACY.md`, pendiente).

---

## 2. Captura en el dispositivo

Secuencia propuesta:

1. **Detección de flanco:** `INPUT_PULLUP`, flanco de bajada. Se ignora cualquier nuevo flanco mientras haya una captura en curso.
2. **Antirrebote:** ventana de 50 ms tras el primer flanco; se confirma si el nivel sigue bajo.
3. **Bloqueo:** mientras exista una captura en vuelo (captura → envío → confirmación) el botón no genera otra. Un LED indica "ocupado".
4. **Captura:** `esp_camera_fb_get()`, resolución **VGA 640×480** por defecto, JPEG calidad 12–15. Fallback a QVGA si falta PSRAM o la memoria libre cae por debajo de un umbral. *Valores a validar con hardware real.*
5. **Validación local:**
   - el buffer no es nulo ni vacío;
   - tamaño entre 5 KB y 512 KB (fuera de rango, se descarta y se informa con un patrón de LED);
   - cabecera `FF D8` y final `FF D9`.
   Una imagen que falla la validación no se envía.
6. **Identificador:** `capture_id` = UUID v4 generado **una vez por pulsación**. Todos los reintentos de esa pulsación reutilizan el mismo `capture_id`.
7. **Envío** (sección 4).
8. **Liberación del buffer** en cuanto termina el intento final, éxito o fallo. No hay cola de capturas pendientes: una foto antigua que se envía tarde puede describir un entorno que ya no existe, y eso es peor que no enviarla.

**Limitaciones que hay que asumir:** la ESP32-CAM tiene RAM limitada y, según la placa, PSRAM opcional. Solo se mantiene **un** frame en memoria a la vez. No se almacenan imágenes en la tarjeta SD ni en flash en esta fase.

---

## 3. Idempotencia y duplicados

- Clave única en backend: `(device_id, capture_id)`.
- Si llega una captura con un `capture_id` ya registrado:
  - si el contenido coincide (mismo tamaño y mismo SHA-256 del cuerpo), responde `200` con el estado actual; no crea evento nuevo;
  - si el contenido no coincide, responde `409 Conflict`; es un error de firmware o un ataque, y se registra.
- Un reintento tras pérdida de respuesta nunca genera un segundo evento ni una segunda notificación push.

---

## 4. Transmisión

**Formato elegido: cuerpo binario `image/jpeg` con metadatos en cabeceras.**

Alternativas evaluadas:

| Opción | Ventajas | Inconvenientes | Decisión |
|---|---|---|---|
| `multipart/form-data` | Estándar, metadatos junto a la imagen | Overhead de construcción en el microcontrolador; más memoria para el límite de boundary | Descartado para el dispositivo |
| JSON con imagen en base64 | Fácil de depurar | +33 % de tamaño; más memoria; más CPU | Descartado |
| Cuerpo binario `image/jpeg` | Sin copias extra; `HTTPClient::sendRequest` lo soporta directamente | Metadatos solo por cabecera | **Elegido** |
| WebSocket / MQTT | Bidireccional | Infraestructura adicional sin necesidad en esta fase | Descartado |

Cabeceras de la petición de captura:
- `Authorization: Bearer <device_token>`
- `Content-Type: image/jpeg`
- `Content-Length: <bytes>`
- `X-Capture-Id: <uuid>`
- `X-Firmware-Version: 0.1.0`
- `X-Capture-Uptime-Ms: <entero>` (informativo)

Límites en backend: 512 KB máximo por captura (`413` si se supera); `Content-Type` distinto de `image/jpeg` → `415`; cuerpo sin cabecera `FF D8` → `422`.

**Reintentos:**
- Timeout de conexión 5 s; timeout total de petición 15 s.
- Reintento solo ante fallo de red, `408`, `429`, `500`, `502`, `503`, `504`.
- Backoff exponencial con jitter: 2 s, 4 s, 8 s (máximo 4 intentos en total). Con `429` o `503` se respeta `Retry-After` si existe.
- Con `4xx` distinto de los anteriores no se reintenta: el firmware informa el error y libera el buffer.

---

## 5. Contrato API

Prefijo: `/api/v1/dispositivos`. Todas las rutas de dispositivo usan `Authorization: Bearer`. Las rutas de usuario usan la sesión actual.

| Método | Ruta | Quién | Propósito |
|---|---|---|---|
| POST | `/vinculaciones` | Usuario | Genera código de vinculación de un solo uso. |
| POST | `/registro` | Dispositivo (código) | Registra el bastón y devuelve su token. |
| GET | `/config` | Dispositivo | Parámetros de captura y versión de API mínima. |
| POST | `/capturas` | Dispositivo | Sube una fotografía (idempotente por `X-Capture-Id`). |
| GET | `/capturas/{captureId}` | Dispositivo | Estado de una captura propia. |
| POST | `/estado` | Dispositivo | Heartbeat: batería, RSSI, memoria libre. |
| POST | `/token/rotar` | Dispositivo | Rota el token. |
| DELETE | `/{deviceId}` | Usuario propietario | Revoca el dispositivo. |
| GET | `/capturas/{captureId}/resultado` | Usuario o auxiliar autorizado | Resultado de IA y estado. (Ruta de consulta, fuera del dispositivo.) |

### 5.1 Ejemplos

**Vinculación (usuario):**

```bash
curl -X POST https://<backend>/api/v1/dispositivos/vinculaciones \
  -H "Cookie: ORBIS_SID=<sesion>"
```

```json
{
  "codigo": "K7QX-3M2P",
  "expiraEn": "2026-11-15T22:10:00Z"
}
```

**Registro (dispositivo):**

```bash
curl -X POST https://<backend>/api/v1/dispositivos/registro \
  -H "Content-Type: application/json" \
  -d '{"codigo":"K7QX-3M2P","modeloHardware":"ESP32-CAM-AI-THINKER","versionFirmware":"0.1.0"}'
```

```json
{
  "deviceId": "3f9b1c2e-8d4a-4f7e-9a21-6b5c0d8e7a10",
  "deviceToken": "mR0x…(43 caracteres base64url, mostrado una sola vez)",
  "tokenValidoHasta": "2027-05-14T00:00:00Z"
}
```

Si el código ya se usó o caducó: `409` o `410` con `{"error":"CODIGO_NO_VALIDO"}` (sin distinguir cuál de las dos cosas pasó, para no revelar códigos válidos).

**Config (dispositivo):**

```bash
curl https://<backend>/api/v1/dispositivos/config \
  -H "Authorization: Bearer <deviceToken>"
```

```json
{
  "resolucion": "VGA",
  "calidadJpeg": 13,
  "tamanoMaximoBytes": 524288,
  "versionApiMinima": "1"
}
```

**Subida de captura (dispositivo):**

```bash
curl -X POST https://<backend>/api/v1/dispositivos/capturas \
  -H "Authorization: Bearer <deviceToken>" \
  -H "Content-Type: image/jpeg" \
  -H "X-Capture-Id: 0b7e5f2a-41c9-4d8e-b3a6-9f1e2c7d4a85" \
  -H "X-Firmware-Version: 0.1.0" \
  --data-binary @captura.jpg
```

Primera recepción → `202 Accepted`:

```json
{
  "captureId": "0b7e5f2a-41c9-4d8e-b3a6-9f1e2c7d4a85",
  "estado": "RECIBIDA",
  "duplicada": false
}
```

Reintento con el mismo contenido → `200 OK` con `"duplicada": true` y el estado actual (por ejemplo `"EN_PROCESO"`).

Errores:

| Situación | Código | Cuerpo |
|---|---|---|
| Token ausente, inválido o revocado | 401 | `{"error":"NO_AUTORIZADO"}` |
| Tamaño > 512 KB | 413 | `{"error":"IMAGEN_DEMASIADO_GRANDE"}` |
| Tipo distinto de JPEG | 415 | `{"error":"TIPO_NO_SOPORTADO"}` |
| Cabecera JPEG inválida | 422 | `{"error":"IMAGEN_NO_VALIDA"}` |
| Mismo `captureId`, contenido distinto | 409 | `{"error":"CAPTURA_CONFLICTO"}` |
| Demasiadas peticiones | 429 | `{"error":"DEMASIADAS_PETICIONES"}` + `Retry-After` |
| Error interno | 500 | `{"error":"ERROR_INTERNO"}` (sin detalles) |

**Estado de captura (dispositivo):**

```json
{
  "captureId": "0b7e5f2a-41c9-4d8e-b3a6-9f1e2c7d4a85",
  "estado": "DESCRITA",
  "recibidaEn": "2026-11-15T21:58:12Z",
  "actualizadaEn": "2026-11-15T21:58:14Z"
}
```

**Heartbeat:**

```json
{
  "bateriaMv": 3920,
  "rssi": -61,
  "memoriaLibreBytes": 182000,
  "uptimeSegundos": 3610
}
```

---

## 6. Estados de procesamiento

```
RECIBIDA ──► EN_PROCESO ──► DESCRITA ──► ENTREGADA
    │             │
    │             └──► FALLIDA (motivo: IA_NO_CONCLUYENTE | IA_NO_DISPONIBLE | TIMEOUT)
    └──────────────► FALLIDA (motivo: IMAGEN_ILEGIBLE)
```

- `RECIBIDA`: imagen validada y persistida; la respuesta HTTP ya no depende del análisis.
- `EN_PROCESO`: análisis lanzado en un worker en segundo plano del propio proceso (una sola instancia, sin colas externas). Una petición HTTP nunca ejecuta el modelo.
- `DESCRITA`: resultado guardado. La descripción es la salida de IA, **no** una afirmación de seguridad (ver `docs/ORBIS_AI_EVALUATION.md`, pendiente).
- `ENTREGADA`: notificación push enviada y confirmada por FCM. Es opcional: el usuario puede consultar el resultado igualmente.
- Las transiciones son idempotentes: repetir una transición ya aplicada no cambia nada.

Un resultado sin detecciones fiables **no** se presenta como "camino despejado". El estado válido es `DESCRITA` con descripción vacía y motivo `SIN_HALLAZGOS_CONFIABLES`.

---

## 7. Retención y privacidad de la imagen

- La imagen se guarda cifrada en reposo si el almacenamiento lo permite; si no, se documenta el riesgo.
- Eliminación automática: a las 24 h tras `ENTREGADA` o `FALLIDA`, con un máximo de 7 días en cualquier caso (valores propuestos; el propietario decide).
- Nunca se escriben bytes de imagen, `device_token` ni cabeceras de autorización en logs.
- Antes de usar almacenamiento persistente en Railway hay que verificar si el servicio tiene volumen persistente; si no, el almacenamiento en disco local se pierde al redeplegar. Pendiente de verificación en la cuenta del propietario.

---

## 8. Pruebas que este contrato exige

- Unitarias (backend): token válido/revocado/rotado; idempotencia (misma captura, mismo y distinto contenido); límites de tamaño y tipo; código de vinculación de un solo uso y caducidad.
- Simuladas (firmware, Wokwi o host): antirrebote; bloqueo durante captura en vuelo; backoff con jitter; liberación de buffer tras fallo.
- Integración local (backend + cliente HTTP de prueba): ciclo completo registro → captura → estado → resultado, con reintentos forzados.
- Hardware real (**no realizadas**): captura OV2640, tiempos de subida con datos móviles, consumo de energía, comportamiento al perder el hotspot.

---

## 9. Qué existe hoy y qué cambia

| Elemento | Estado actual | Cambio propuesto |
|---|---|---|
| Autenticación de dispositivo | `X-Device-Token` único compartido | Token por dispositivo con hash, revocación y rotación |
| Endpoint de eventos | `POST /api/v1/eventos` con distancia | Se mantiene para el simulador web; el dispositivo nuevo usa `/api/v1/dispositivos/capturas` |
| Firmware | `hardware/wokwi/sketch.ino`: sensor ultrasónico y envío de eventos con token embebido | Se conserva el sensor como función independiente; se añade el módulo de cámara. El token embebido debe eliminarse en cuanto exista registro |
| Imágenes | `imagenUrl` es TEXT y se marca como "base64 temporal" | Almacenamiento separado de bytes con retención y eliminación explícitas |

---

## 10. Decisiones abiertas (requieren hardware o propietario)

1. Resolución y calidad JPEG definitivas (medir tiempo de captura y tamaño en la placa real).
2. Tiempo máximo aceptable de respuesta desde el botón hasta el aviso de "recibida" (propuesta: < 2 s con hotspot activo).
3. Dónde almacenar imágenes en Railway (volumen persistente o almacenamiento de objetos externo; ambos implican coste o configuración del propietario).
4. Si el usuario quiere confirmación sonora en el propio bastón además de la app.
