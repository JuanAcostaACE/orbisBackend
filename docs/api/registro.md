# POST /auth/registro

Crea un usuario. Público, sin CSRF.

## Cuerpo

```json
{ "nombre": "Ana Pérez", "correo": "ana@ejemplo.com", "contrasena": "minimo8caracteres" }
```

| Campo | Reglas |
|---|---|
| `nombre` | obligatorio, 2–60 caracteres |
| `correo` | obligatorio, formato email, máx. 120 |
| `contrasena` | obligatorio, 8–100 caracteres; se guarda con BCrypt |

## Respuesta 201

```json
{ "id": 1, "nombre": "Ana Pérez", "correo": "ana@ejemplo.com", "mensaje": "Registro exitoso" }
```

## Errores

- `400` campos inválidos o correo ya registrado.
