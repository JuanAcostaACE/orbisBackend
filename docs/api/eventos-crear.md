# POST /api/v1/eventos

Registra un evento de detección. Lo usan el ESP32 (con `X-Device-Token`) y el simulador web (con sesión).
El propietario del evento **nunca** se toma del cuerpo: sale de la sesión o de `APP_DEVICE_OWNER_EMAIL`.

## Cuerpo

```json
{ "modo": "ACTIVO", "distanciaCm": 45.5, "etiquetasIA": "person,chair" }
```

| Campo | Reglas |
|---|---|
| `modo` | obligatorio, `PASIVO` o `ACTIVO` |
| `distanciaCm` | obligatorio, 0–500 |
| `etiquetasIA` | opcional, máx. 2000 caracteres (solo en `ACTIVO`) |

## Respuesta 201

Un `EventoResponse`: `id`, `modo`, `distanciaCm`, `etiquetasIA`, `fechaHora`,
`obstaculoEtiqueta`, `obstaculoNivel`.

## Errores

- `400` validación. `401` sin sesión ni token válido. `503` fallo de visión.
