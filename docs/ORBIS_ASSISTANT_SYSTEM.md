# ORBIS — Sistema de auxiliares y permisos

Estado: **diseño funcional propuesto**. Solo existen en código la autenticación de usuarios y el aislamiento de datos por propietario. La vinculación con auxiliares no está implementada.

## Principio

Un auxiliar **no** puede acceder a un usuario por conocer su identificador. Todo acceso se autoriza con una vinculación explícita, revocable y registrada.

## Modelo de datos (propuesto)

- **Vinculación:** `usuario_principal`, `auxiliar`, `estado` (INVITADA, ACTIVA, REVOCADA), `permisos`, `creada_en`, `aceptada_en`, `revocada_en`.
- **Permisos:** `VER_CAPTURAS`, `VER_RESULTADOS`, `CORREGIR_DESCRIPCIONES`, `VER_EVENTOS`. Por defecto ninguno.
- **Registro de acciones:** cada consulta de imagen, cada corrección y cada cambio de permisos queda con actor, fecha y objeto.

## Flujo

1. **Invitación:** el usuario principal invita a un auxiliar por correo y elige permisos.
2. **Aceptación:** el auxiliar acepta con su sesión. Hasta entonces no ve nada.
3. **Consulta:** cada petición comprueba la vinculación ACTIVA y el permiso concreto. Una captura ajena responde 404, igual que una inexistente.
4. **Corrección:** ver la sección siguiente.
5. **Revocación:** el usuario principal revoca. La siguiente petición del auxiliar falla. Las vinculaciones revocadas no se borran: quedan como historial.

## Correcciones: nunca sobrescribir el original

Ejemplo: la IA describe una puerta, pero el auxiliar determina que es una ventana.

Para cada corrección se guarda:

- **Descripción original de la IA** (inmutable).
- **Descripción corregida** y quién la escribió.
- **Quién corrigió** (auxiliar concreto) y **cuándo**.
- **Si la persona usuaria recibió la nueva descripción** (estado de entrega).

La persona usuaria recibe la corrección como una nueva descripción, con aviso de que la anterior fue corregida. Nunca se cambia en silencio el resultado que ya escuchó.

## Historial

Las capturas y resultados se conservan según la política de retención (`ORBIS_SECURITY_AND_PRIVACY.md`). El registro de acciones de auxiliares se conserva más tiempo que las imágenes, porque no contiene imágenes ni datos de salud, solo quién hizo qué y cuándo.

## Riesgos y decisiones pendientes

- Un auxiliar con permiso de corrección puede escribir descripciones falsas. Mitigación: el original siempre queda visible y la corrección queda firmada por el auxiliar.
- Revocar no borra lo ya visto. Esto debe comunicarse al usuario.
- Cuántos auxiliares puede tener un usuario: pendiente de decisión del propietario.
- Si el auxiliar puede ver imágenes o solo descripciones: pendiente (propuesta: imágenes solo con permiso explícito).
