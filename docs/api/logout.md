# POST /auth/logout

Invalida la sesión actual y limpia el `SecurityContext`.

## Respuesta 200

```json
{ "message": "Sesión cerrada correctamente" }
```

Es idempotente: si no hay sesión, responde igual.
