# ORBIS — Registro de decisiones

Formato: problema, alternativas, decisión, motivo, consecuencias, riesgos, fecha, responsable.

---

## D-001 — Formato de subida de fotografías

- **Fecha:** 2026-11-15
- **Responsable:** agente de arquitectura (coordinador)
- **Problema:** la ESP32-CAM debe enviar una fotografía al backend con metadatos y reintentos, con poca RAM.
- **Alternativas:** multipart/form-data; JSON con base64; cuerpo binario `image/jpeg` con cabeceras; WebSocket o MQTT.
- **Decisión:** cuerpo binario `image/jpeg` con `X-Capture-Id` y cabeceras de metadatos.
- **Motivo:** sin copias extra de memoria ni overhead de base64; soportado por `HTTPClient` y por Spring sin dependencias nuevas.
- **Consecuencias:** los metadatos viajan en cabeceras; el backend valida tipo y tamaño manualmente.
- **Riesgos:** cabeceras con límites de tamaño según proxy de Railway (pendiente de verificar con la imagen real).

## D-002 — Identidad por dispositivo en lugar de token global

- **Problema:** el esquema actual `X-Device-Token` es un único valor compartido por todos los dispositivos.
- **Alternativas:** mantener token global; token por dispositivo con código de vinculación; certificados de cliente (mTLS).
- **Decisión:** token por dispositivo, emitido una vez tras un código de vinculación de un solo uso; hash en servidor; revocación y rotación.
- **Motivo:** cumple la exigencia de identidad propia y revocable sin infraestructura de PKI. mTLS se descarta en esta fase porque complica la configuración del proxy de Railway y el aprovisionamiento.
- **Consecuencias:** se necesita una tabla de dispositivos y endpoints de registro, revocación y rotación.
- **Riesgos:** un token extraído de la memoria flash sigue siendo válido hasta su revocación. Mitigación: revocación desde la app, caducidad de 180 días. Pendiente: cifrado en NVS.

## D-003 — Procesamiento de IA en segundo plano dentro del mismo proceso

- **Problema:** el análisis de imagen puede tardar más que una petición HTTP razonable y no debe bloquear el backend.
- **Alternativas:** análisis síncrono en la petición; cola externa (Redis, RabbitMQ); worker en segundo plano del mismo proceso; servicio de inferencia separado.
- **Decisión:** worker en segundo plano dentro del mismo proceso, con estados persistidos y transiciones idempotentes.
- **Motivo:** una sola instancia de Railway durante bastante tiempo; no se justifica infraestructura distribuida.
- **Consecuencias:** si el proceso se reinicia durante `EN_PROCESO`, las capturas quedan en ese estado y hay que reanudarlas al arrancar (pendiente de implementar).
- **Riesgos:** pérdida de trabajo en reinicios; la recuperación al arrancar debe probarse.

## D-004 — No cola de capturas en el dispositivo

- **Problema:** qué hacer con una captura que no pudo enviarse.
- **Alternativas:** cola persistente en flash; descartar tras reintentos.
- **Decisión:** descartar tras el último intento y avisar con LED.
- **Motivo:** una descripción tardía puede referirse a un entorno que ya no existe; la memoria de la ESP32-CAM no permite colas grandes.
- **Consecuencias:** el usuario debe volver a pulsar el botón.
- **Riesgos:** pérdidas en cortes prolongados de conexión; hay que comunicarlo claramente al usuario.

## D-005 — Auto del selector de cámara: no bloquea el producto

- **Fecha:** 2026-11-15
- **Problema:** el modo Auto de la cámara en el panel web (COCO-SSD) tiene cadencia irregular: 2 a 3 pares de envíos separados menos de 2 s en cada una de 10 repeticiones de K5, y en 2 de ellas la mediana queda fuera de 4–6 s.
- **Alternativas:** (a) medir desde el fin de cada ciclo en lugar de un temporizador fijo; (b) aceptar la irregularidad y relajar el criterio; (c) retirar Auto del panel.
- **Decisión:** no aplicar cambios a la lógica de distancia ni histéresis. Documentar el fallo como **abierto**. La captura individual por botón es el flujo principal del bastón; Auto es una función experimental del panel y no bloquea la fase.
- **Motivo:** el hilo principal del navegador se bloquea durante la inferencia; el parche (a) no resuelve la latencia y el criterio de cero pares cortos es estricto para un modo experimental.
- **Consecuencias:** K5 queda FAIL en el informe; K3c (desconexión con inferencia en curso) pasa 10/10.
- **Riesgos:** si Auto se usa como función pública, la cadencia es incorrecta. Revisar al decidir si el panel lo mantiene.

## D-006 — Diseño accesible primero

- **Problema:** la aplicación móvil debe usarse sin mirar la pantalla.
- **Decisión:** los prototipos visuales se diseñan con objetivos de tamaño grandes, contraste alto, texto corto y estados comunicados por voz y por texto, nunca solo por color.
- **Motivo:** requisito de producto, no característica posterior.
- **Consecuencias:** los mockups deben validarse con TalkBack en un dispositivo real (pendiente).
