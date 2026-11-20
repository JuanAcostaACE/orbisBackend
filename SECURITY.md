# Política de seguridad — ORBIS Backend

## Reportar una vulnerabilidad

No abras un issue público. Escribe a cualquiera de los integrantes del equipo
(ver README) con:

- Descripción del problema y endpoint afectado.
- Pasos para reproducirlo.
- Impacto estimado.

Respondemos en un plazo de 5 días hábiles.

## Alcance

- API REST (`/auth/**`, `/api/v1/**`, `/health`).
- Autenticación de dispositivos ESP32 (`X-Device-Token`).
- Firmware en `hardware/`.

## Medidas actuales

| Área | Medida |
|---|---|
| Sesión | Cookie `ORBIS_SID` HttpOnly, SameSite=Strict, Secure en prod, solo por cookie |
| CSRF | Token en cookie `XSRF-TOKEN` para mutaciones con sesión |
| Contraseñas | BCrypt (costo 12) |
| CORS | Orígenes, métodos y cabeceras explícitos |
| Cabeceras | HSTS, nosniff, Referrer-Policy, Permissions-Policy, CSP |
| Errores | Respuestas JSON sin trazas ni mensajes internos |
| Dependencias | Dependabot semanal y CodeQL |

## Secretos

Nunca subas `.env`, llaves, keystores ni `secrets.h`. Si un secreto llega al
repositorio, rótalo de inmediato aunque se borre el commit.
