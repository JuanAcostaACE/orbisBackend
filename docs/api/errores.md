# Formato de errores

Todas las respuestas de error tienen la forma:

```json
{ "status": 400, "message": "Descripción legible", "timestamp": "2026-11-20T10:00:00" }
```

| Código | Origen |
|---|---|
| 400 | Validación `@Valid` fallida o regla de negocio (`IllegalArgumentException`) |
| 401 | Sin autenticación o credenciales incorrectas (mensaje genérico) |
| 403 | Autenticado sin permiso, o falta el token CSRF |
| 404 | Recurso inexistente o de otro usuario |
| 501 | Operación no implementada |
| 503 | Servicio de visión artificial no disponible |
| 500 | Error interno; nunca expone trazas ni SQL |
