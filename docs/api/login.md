# POST /auth/login

Valida credenciales y crea la sesión. Público, sin CSRF.

## Cuerpo

```json
{ "correo": "ana@ejemplo.com", "contrasena": "minimo8caracteres" }
```

## Respuesta 200

Envía la cookie `ORBIS_SID` y devuelve:

```json
{ "id": 1, "nombre": "Ana Pérez", "correo": "ana@ejemplo.com", "mensaje": "Login exitoso" }
```

## Errores

- `400` cuerpo inválido.
- `401` credenciales incorrectas. El mensaje es el mismo exista o no el correo.
