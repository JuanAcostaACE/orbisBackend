# GET /api/v1/eventos/{id}

Devuelve un evento si pertenece al usuario autenticado.

## Respuestas

- `200` `EventoResponse`.
- `404` si no existe **o** es de otro usuario (no se distingue, para no filtrar información).
