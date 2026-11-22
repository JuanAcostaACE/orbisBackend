# POST /api/v1/events

Registra un evento de detección. Lo usan el ESP32 (con `X-Device-Token`) y el simulador web (con sesión).
El propietario del evento **nunca** se toma del cuerpo: sale de la sesión o de `APP_DEVICE_OWNER_EMAIL`.

## Cuerpo

```json
{ "mode": "ACTIVE", "distanceCm": 45.5, "aiLabels": "person,chair" }
```

| Campo | Reglas |
|---|---|
| `mode` | obligatorio, `PASSIVE` o `ACTIVE` |
| `distanceCm` | obligatorio, 0–500 |
| `aiLabels` | opcional, máx. 2000 caracteres (solo en `ACTIVE`) |

## Respuesta 201

Un `EventResponse`: `id`, `mode`, `distanceCm`, `aiLabels`, `createdAt`,
`obstacleLabel`, `obstacleLevel`.

## Errores

- `400` validación. `401` sin sesión ni token válido. `503` fallo de visión.
