# GET /auth/me

Devuelve el usuario de la sesión actual. Lo usa el frontend al cargar para saber si ya hay sesión.

## Respuesta 200

```json
{ "email": "ana@ejemplo.com" }
```

## Errores

- `401` sin sesión.
