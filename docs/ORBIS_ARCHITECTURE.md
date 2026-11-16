# ORBIS — Arquitectura

Estado: documento de arquitectura. Lo marcado como **implementado** está en código y probado; lo marcado como **propuesto** no existe todavía.

## Componentes

```
 ┌─────────────┐   Wi-Fi (hotspot del teléfono)   ┌──────────────────────────┐
 │ ESP32-CAM   │ ───── POST imagen (HTTPS) ─────▶ │                          │
 │ (bastón)    │ ◀──── GET config / estado ────── │   Backend ORBIS          │
 └─────────────┘                                  │   Spring Boot en Railway │
                                                  │   (una instancia)        │
 ┌─────────────┐   HTTPS + sesión / token          │                          │
 │ App Android │ ◀────── resultados, push ──────▶ │   - usuarios, sesiones   │
 │ (teléfono)  │                                  │   - dispositivos         │
 └─────────────┘                                  │   - capturas, eventos    │
        ▲ Bluetooth (solo audífonos)              │   - resultados de IA     │
 ┌─────────────┐                                  │                          │
 │ Audífonos   │                                  └──────────────────────────┘
 └─────────────┘                                        ▲
 ┌─────────────┐   HTTPS + sesión                       │
 │ Panel web / │ ───────────────────────────────────────┘
 │ auxiliar    │
 └─────────────┘
```

Regla fija: **el bastón y la aplicación móvil no se conectan entre sí.** Ambos hablan solo con el backend.

## Estado por componente

| Componente | Estado | Dónde está |
|---|---|---|
| Usuarios, login, sesiones, cuenta bloqueada | Implementado | `orbisBackend` (rama `fase1-seguridad-bugs`) |
| Eventos de distancia (simulador web y token legado) | Implementado | `POST /api/v1/eventos` |
| Identidad por dispositivo (D-002) | Implementado (ver commit de la rama de dispositivos) | `DispositivoService`, `DispositivoAuthFilter` |
| Recepción de fotografías idempotente | Implementado | `POST /api/v1/dispositivos/capturas` (rama `feat/captura-fotos`) |
| Procesamiento de IA en segundo plano | **Propuesto** | Worker en el mismo proceso, sin cola externa |
| Almacenamiento de bytes de imagen | **Propuesto** | Pendiente de decisión del propietario |
| Panel del auxiliar | **Propuesto** | Ver `ORBIS_ASSISTANT_SYSTEM.md` |
| Aplicación Android | **Propuesto** | Ver `ORBIS_ANDROID_STRATEGY.md` |
| Firmware de captura con cámara | **Propuesto** | Ver `ORBIS_DEVICE_PROTOCOL.md` |
| Detección local de proximidad | **Propuesto, investigación** | Sensores ultrasónicos, sin validar en hardware |

## Flujo principal (objetivo)

1. El usuario pulsa el botón del bastón.
2. El bastón toma una fotografía, la valida y la envía con un `X-Capture-Id` único.
3. El backend valida, registra la captura en estado `RECIBIDA` y responde `202`. No ejecuta el modelo dentro de la petición.
4. Un worker en segundo plano pasa la captura a `EN_PROCESO`, pide el análisis y guarda el resultado (`DESCRITA`) o un fallo (`FALLIDA`).
5. La app recibe una notificación push (cuando exista) y consulta el resultado con su sesión.
6. La app reproduce la descripción por voz.
7. El auxiliar puede consultar la misma captura y su resultado, y corregirlo.

## Decisiones de arquitectura

Las decisiones están en `ORBIS_DECISION_LOG.md`. Resumen:

- D-001: subida binaria `image/jpeg` con metadatos en cabeceras.
- D-002: identidad por dispositivo con código de vinculación, token con hash y revocación.
- D-003: procesamiento en segundo plano dentro del mismo proceso, sin cola externa.
- D-004: el bastón no guarda cola de capturas pendientes.

## Límites conocidos

- **Una sola instancia.** Los contadores de límite de peticiones están en memoria. Con varias instancias habría que cambiarlos.
- **Almacenamiento efímero.** Si el disco de Railway no es persistente, las imágenes en disco local se pierden al redesplegar. Hay que verificar el servicio antes de guardar imágenes ahí.
- **Reinicios durante procesamiento.** Una captura en `EN_PROCESO` al reiniciar debe reanudarse o marcarse como fallida. Pendiente.
- **Tiempo de dispositivo.** El ESP32 no tiene reloj fiable sin NTP. El backend usa su propio tiempo.
