# ORBIS — Seguridad y privacidad

Estado: análisis y controles. Cada control está marcado como **implementado**, **parcial** o **pendiente**, según el código real.

## Activos

- Fotografías de entornos reales (pueden mostrar personas, domicilios, documentos).
- Descripciones y eventos (pueden revelar hábitos, ubicación aproximada, salud).
- Credenciales de usuarios y tokens de dispositivo.
- Vinculaciones entre usuarios y auxiliares.

## Controles

| Control | Estado | Detalle |
|---|---|---|
| Contraseñas con BCrypt | Implementado | `SecurityConfig` |
| Sesión HttpOnly, SameSite=Strict | Implementado | Cookie `ORBIS_SID` |
| Bloqueo de cuenta por intentos | Implementado | `BloqueoCuentaService` (5 fallos en 15 min) |
| Límite de peticiones por IP | Implementado, en memoria | `RateLimitFilter`; no compartido entre instancias |
| Token compartido de dispositivo | **Riesgo abierto** | `X-Device-Token`; sustituido para capturas por token propio, pero el legado sigue activo |
| Token propio por dispositivo | Implementado | `DispositivoService`: hash SHA-256, caducidad 180 días, revocación, rotación |
| Código de vinculación | Implementado | Un solo uso, 10 min, guardado como hash, bloqueo pesimista al consumir |
| Idempotencia de capturas | Implementado | Clave (propietario, captureId); conflicto 409 |
| Validación de imágenes | Implementado | Tipo, tamaño 5–512 KB, marcadores JPEG |
| Lectura acotada del cuerpo | Implementado | Nunca más de 512 KB + 1 byte en memoria |
| Aislamiento por propietario | Implementado | Capturas y dispositivos solo visibles para su dueño |
| Eventos de dispositivo con propietario real | Implementado | Un principal `device:` nunca se usa como propietario |
| HTTPS en el firmware | **Pendiente** | Requiere validación del certificado con `setCACert()` |
| Cifrado del token en el ESP32 (NVS) | **Pendiente** | Depende del firmware real |
| Almacenamiento cifrado de imágenes | **Pendiente** | Sin decisión de almacenamiento |
| URLs firmadas o acceso autenticado a imágenes | **Pendiente** | Propuesta: acceso solo por API con sesión |
| Retención y borrado de imágenes | **Pendiente** | Propuesta: 24 h tras entrega, máximo 7 días |
| Registro de accesos a imágenes | **Pendiente** | Propuesta: quién, cuándo, qué captura |
| Rotación de secretos de despliegue | **Pendiente, requiere propietario** | Token del entorno de Railway |

## Riesgos conocidos

1. **Token legado compartido.** Cualquier bastón con el token legado puede generar eventos a nombre del propietario configurado. Mitigación: retirarlo cuando todos los bastones usen token propio.
2. **Token extraído del firmware.** Un token guardado en claro en la flash puede extraerse. Mitigación: revocación desde la app y caducidad de 180 días; cifrado en NVS como mejora.
3. **Código de vinculación de baja entropía** (~39 bits). Mitigación: caducidad de 10 minutos, un solo uso y bloqueo al consumir. Un atacante con muchas peticiones podría intentar adivinarlo; el límite por IP debe aplicarse también a `/registro` (pendiente).
4. **Fallo en el proxy de Railway.** La IP real del cliente depende de la cabecera `X-Real-IP` solo si el proxy es de confianza (`RemoteIpValve`). Pendiente de verificación en el entorno de Railway.
5. **Imágenes persistidas por error.** Un log o una excepción podría incluir bytes de imagen. Mitigación: no registrar cuerpos de petición; revisar las excepciones del controlador.

## Privacidad

- No registrar fotografías completas, tokens ni cabeceras de autorización en logs.
- No publicar información personal en reportes de prueba: los datos de prueba son correos ficticios (`@test.com`).
- Minimizar: no guardar datos más tiempo del necesario.
- Consentimiento: pendiente de texto y de decisión del propietario sobre la política de privacidad.
- Una persona identificable en una imagen no debe aparecer en un informe, captura de pantalla o prueba compartida.

## Fiabilidad frente a fallos

- Reintentos con backoff y `Retry-After` en el firmware (ver protocolo).
- Idempotencia: un reintento tras pérdida de respuesta no crea evento duplicado.
- Un fallo de IA no se presenta como "camino despejado".
- Pérdida de conexión: el bastón descarta la captura tras el último intento y avisa con LED. No hay cola.
- Reinicio del backend durante procesamiento: pendiente de implementar la reanudación.

## Riesgos no resolubles en esta fase

- Seguridad física del bastón (robo, manipulación del módulo).
- Dependencia de un solo proveedor de hosting.
- Precisión de la IA: los errores de descripción son un riesgo para la persona usuaria y se comunican como limitaciones, no se eliminan.
