# ORBIS — Estrategia Android

Estado: **comparación técnica y recomendación**. No se ha construido ningún prototipo Android en esta fase: no hay Android SDK verificado en el entorno de trabajo. Las afirmaciones sobre la plataforma se basan en su documentación general y deben confirmarse con la documentación oficial antes de comprometer la arquitectura.

## Requisitos que deciden la tecnología

- Accesibilidad con TalkBack como requisito, no como añadido.
- Síntesis de voz fiable, con control de velocidad e interrupción de mensajes.
- Notificaciones push (FCM) que lleguen aunque la app no esté abierta.
- Recuperación de resultados pendientes tras pérdida de conexión o reinicio.
- Audífonos Bluetooth como salida de audio.
- Sesión y autenticación frente al backend.
- Batería: no mantener procesos activos sin necesidad.

## Opciones

| Criterio | Kotlin + Jetpack Compose (nativo) | Capacitor + TypeScript (web en contenedor) |
|---|---|---|
| TalkBack y semántica de accesibilidad | Control directo sobre roles, descripciones y orden de foco | Depende de la calidad del WebView y del HTML; más difícil de auditar |
| TTS | API nativa `TextToSpeech`, con control de cola e interrupción | Plugin de terceros o Web Speech API del WebView, con soporte irregular |
| Push (FCM) | SDK oficial | Plugin de terceros |
| Segundo plano y reinicio | `WorkManager`, servicios; comportamiento documentado | Depende del plugin; más opaco |
| Bluetooth audio | Acceso directo a perfiles de audio | Requiere plugin o código nativo de todas formas |
| Reutilización del frontend actual | Ninguna | Parcial (TypeScript del panel), pero el panel no está diseñado para uso sin mirar la pantalla |
| Mantenimiento | Un lenguaje, un SDK | Dos ecosistemas (web y nativo) mezclados |

## Recomendación

**Kotlin + Jetpack Compose**, por accesibilidad, TTS, push y segundo plano. El coste es no reutilizar el código del panel web. Esto es coherente con el criterio de la misión: la fiabilidad y la accesibilidad pesan más que reutilizar código.

Capacitor queda descartado para la APK porque delega en plugins justo en las partes que más importan (voz, push, segundo plano). Podría reconsiderarse solo si una prueba demuestra que el WebView cumple TalkBack y TTS en un dispositivo real.

## Pantallas mínimas (propuesta)

Las mismas del prototipo visual (`docs/` y el artefacto de diseño): lista, analizando, resultado, error recuperable. Cada estado se comunica por voz y por texto con `role` de accesibilidad.

## Lo que no está verificado

- Comportamiento real de TalkBack con las pantallas propuestas.
- Latencia de TTS en el dispositivo objetivo.
- Recepción de push con la app cerrada en el modelo de batería del teléfono del usuario.
- Mantenimiento de la conexión Bluetooth de audífonos durante el uso.

Todo ello requiere un teléfono Android real. Queda pendiente.
