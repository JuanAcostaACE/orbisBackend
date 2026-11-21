# POST /auth/register

Crea un usuario. Público, sin CSRF.

## Cuerpo

```json
{ "name": "Ana Pérez", "email": "ana@ejemplo.com", "password": "minimo8caracteres" }
```

| Campo | Reglas |
|---|---|
| `name` | obligatorio, 2–60 caracteres |
| `email` | obligatorio, formato email, máx. 120 |
| `password` | obligatorio, 8–100 caracteres; se guarda con BCrypt |

## Respuesta 201

```json
{ "id": 1, "name": "Ana Pérez", "email": "ana@ejemplo.com", "message": "Registro exitoso" }
```

## Errores

- `400` campos inválidos o correo ya registrado.
