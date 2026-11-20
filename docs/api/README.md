# API de ORBIS Backend

Referencia de los endpoints HTTP expuestos por el backend (Spring Boot 4, Java 21).

| Método | Ruta | Acceso | Documento |
|---|---|---|---|
| POST | `/auth/registro` | Público | [registro.md](registro.md) |
| POST | `/auth/login` | Público | [login.md](login.md) |
| POST | `/auth/logout` | Sesión | [logout.md](logout.md) |
| GET | `/auth/me` | Sesión | [me.md](me.md) |
| POST | `/api/v1/eventos` | Sesión o `X-Device-Token` | [eventos-crear.md](eventos-crear.md) |
| GET | `/api/v1/eventos` | Sesión | [eventos-listar.md](eventos-listar.md) |
| GET | `/api/v1/eventos/{id}` | Sesión | [eventos-obtener.md](eventos-obtener.md) |
| GET | `/api/v1/eventos/estado` | Sesión | [eventos-estado.md](eventos-estado.md) |
| GET | `/health` | Público | [health.md](health.md) |

Ver también: [autenticacion.md](autenticacion.md) y [errores.md](errores.md).
