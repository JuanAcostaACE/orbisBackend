# POST /auth/login

Valida credenciales y crea la sesión. Público, sin CSRF.

## Cuerpo

```json
{ "email": "ana@ejemplo.com", "password": "minimo8caracteres" }
```

## Respuesta 200

Envía la cookie `ORBIS_SID` y devuelve:

```json
{ "id": 1, "name": "Ana Pérez", "email": "ana@ejemplo.com", "message": "Login exitoso" }
```

## Errores

- `400` cuerpo inválido.
- `401` credenciales incorrectas. El mensaje es el mismo exista o no el correo.
