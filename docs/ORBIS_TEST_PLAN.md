# ORBIS — Plan de pruebas

Cada prueba se clasifica como **unitaria**, **simulada**, **integración local**, **hardware real** o **producción**. Una prueba simulada nunca se presenta como prueba física.

## Estado actual de las pruebas

| Área | Tipo | Cantidad | Estado |
|---|---|---|---|
| Backend (suite completa Maven) | Unitaria + integración con H2 | 181 | Verde en feat/captura-fotos (15d92bf) |
| Dispositivos (D-002) | Unitaria + integración HTTP | 16 (10 servicio, 6 HTTP) | Verde; suite completa 181/181 (commit 15d92bf) |
| Captura fotográfica | Unitaria + HTTP | 21 | Verde |
| Panel web (Playwright, cámara) | Simulada (dispositivos falsos en navegador) | K3, K4, K7 10/10; K5 falla; K3c 10/10 | Ver `ORBIS_NEXT_STEPS.md` |
| Firmware (captura, botón, reintentos) | Simulada (Wokwi/host) | 0 | **Pendiente** |
| Android | Unitaria, TalkBack, TTS | 0 | **Pendiente**, sin proyecto |
| Hardware OV2640 y sensores | Hardware real | 0 | **Pendiente**, sin módulo |
| Logitech C920 en el selector del panel | Hardware real | 0 | **Pendiente**, guía en `docs/GUIA_PRUEBA_C920.md` |

## Qué debe cubrir cada área

**Backend.** Autenticación de usuarios, de dispositivos (válido, revocado, caducado, rotado), código de vinculación (uso único, caducidad), subida de imágenes (tipo, tamaño, JPEG válido), idempotencia (reintento idéntico y conflicto), permisos de auxiliares (pendiente), estados de procesamiento (pendiente), errores y recuperación (pendiente).

**Firmware.** Pulsación y rebote, captura, imagen vacía o truncada, conexión, pérdida de conexión, reintentos con backoff, confirmación, memoria libre, recuperación tras reinicio.

**Android.** Login, recepción de resultados, consulta, TTS, TalkBack, notificaciones, sesión expirada, sin Internet, audífonos Bluetooth, errores de procesamiento.

**Integración.** Captura real, subida, recepción del backend, procesamiento, notificación, consulta del resultado, reproducción de voz, consulta por auxiliar.

## Métricas

Medir y reportar, con el método escrito en la prueba:

- Tiempo desde la pulsación hasta la recepción en el backend.
- Tiempo de subida.
- Tiempo de inferencia.
- Tiempo desde el resultado hasta la reproducción.
- Porcentaje de solicitudes fallidas.
- Porcentaje de imágenes procesadas correctamente.
- Consumo aproximado de datos por captura.
- Consumo energético, solo si es medible.

Ninguna métrica de hardware se da por válida sin el hardware conectado.

## Reglas

- Las pruebas no deben debilitar sus aserciones para pasar.
- Las pruebas de producción no se ejecutan sin autorización.
- Los datos de prueba son ficticios y no contienen personas identificables.
- Cada cambio de código debe ir acompañado de su prueba, o documentar por qué no es posible.
