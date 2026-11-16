# ORBIS — Próximos pasos

Estado del 2026-11-15. Clasificado según la misión autónoma.

## Críticos

1. Implementar el modelo de dispositivo (tabla, hash, código de vinculación, revocación) según `ORBIS_DEVICE_PROTOCOL.md`, con tests de integración locales.
2. Eliminar el token compartido `X-Device-Token` del firmware y del backend una vez exista el registro por dispositivo.
3. Decidir el almacenamiento de imágenes en Railway (volumen persistente o almacenamiento de objetos). Requiere verificación en la cuenta del propietario.

## Importantes

4. Endpoint `POST /api/v1/dispositivos/capturas` con idempotencia y validación JPEG.
5. Worker de procesamiento en segundo plano con recuperación de estados al arrancar.
6. Resolver K5 (cadencia de Auto) o retirar Auto del panel.
7. Prototipo de APK con TTS y TalkBack, comparando Kotlin/Compose y Capacitor.

## Secundarios

8. Redactar `ORBIS_AI_EVALUATION.md`, `ORBIS_ANDROID_STRATEGY.md`, `ORBIS_ASSISTANT_SYSTEM.md`, `ORBIS_SECURITY_AND_PRIVACY.md`, `ORBIS_TEST_PLAN.md`, `ORBIS_ARCHITECTURE.md` y `ORBIS_PRODUCT_VISION.md`. Pendientes en esta sesión.
9. Migrar de la tabla `imagenUrl` (base64 temporal) a almacenamiento separado.

## Bloqueados por hardware

10. Captura OV2640 real, tiempos de subida con hotspot, consumo energético.
11. Prueba física de la Logitech C920 en el selector de cámaras (guía pendiente de redactar).
12. Sensores ultrasónicos: validar distancia mínima, ángulo y falsos negativos con objetos reales.

## Bloqueados por decisiones del propietario

13. Política de retención de imágenes (propuesta: 24 h tras entrega, máximo 7 días).
14. Tecnología de la APK (Kotlin/Compose frente a Capacitor).
15. Proveedor de IA y si se permite inferencia en servidor frente a móvil.
16. Confirmación sonora en el bastón además de la app.
