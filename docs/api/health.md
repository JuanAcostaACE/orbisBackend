# GET /health

Comprobación de vida usada por Railway. Público.

## Respuesta 200

```json
{ "status": "UP", "service": "ORBIS Backend", "timestamp": "2026-11-20T10:00:00" }
```

Solo admite `GET`; cualquier otro método responde **405** en JSON.
