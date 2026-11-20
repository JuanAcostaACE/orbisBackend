# ORBIS — Visión de producto

Estado: vigente desde la misión de ingeniería del 2026-11-15. No cambia el propósito del producto sin autorización del propietario.

## Qué es

ORBIS es un sistema de asistencia para personas con discapacidad visual. Su pieza principal es un **módulo físico que se acopla a un bastón blanco convencional**. El usuario pulsa un botón, el módulo toma **una fotografía**, el backend la interpreta con IA y el resultado llega a la persona **por voz**.

No es una plataforma de vigilancia ni una transmisión de vídeo continua. No hace capturas periódicas.

## Usuarios

- **Persona usuaria (directa):** persona con discapacidad visual. Usa el bastón y la aplicación Android sin mirar la pantalla.
- **Persona auxiliar (vinculada):** familiar, cuidador o profesional que el usuario autoriza. Puede consultar capturas y resultados, verificar descripciones y corregirlas.
- **Propietario del proyecto:** decide prioridades, costes y despliegues.

## Propuesta de valor

Describir el entorno de forma breve y fiable, cuando la persona lo pide, sin depender de ver la pantalla.

Lo que ORBIS **no** promete:
- No garantiza que un camino esté libre. Que el modelo no detecte un objeto no significa que no haya obstáculo.
- No sustituye al bastón ni a la percepción propia de la persona.
- No detecta peligros por sí solo. La detección de proximidad local es una función separada (ver `ORBIS_ASSISTANT_SYSTEM.md`).

## Principios

1. **Captura solicitada, no continua.** Una pulsación, una fotografía.
2. **Accesible desde el diseño.** Uso sin mirar la pantalla, TalkBack, mensajes breves, estados comunicados por voz y texto.
3. **No inventar.** Si la imagen no permite describir algo, se dice que no se pudo interpretar.
4. **Mínimo de datos.** Las imágenes se conservan solo el tiempo necesario.
5. **Una sola instancia, sin infraestructura prematura.** Railway con un proceso durante bastante tiempo.
6. **Arquitectura fijada:** el bastón y el teléfono hablan solo con el backend. Bluetooth solo para audífonos.

## Éxito del primer prototipo

Un bastón con un módulo real que, al pulsar el botón, produce una descripción que la persona escucha en la app, con tiempos medidos y fallos documentados. Hasta entonces, ningún resultado se presenta como validado en hardware real.

## Pendientes de decisión del propietario

- Política de retención de imágenes.
- Tecnología de la APK (ver `ORBIS_ANDROID_STRATEGY.md`).
- Proveedor de IA y si el análisis se hace en servidor o en el teléfono (ver `ORBIS_AI_EVALUATION.md`).
- Nivel objetivo de accesibilidad (propuesta: WCAG 2.2 AA en el panel web).
