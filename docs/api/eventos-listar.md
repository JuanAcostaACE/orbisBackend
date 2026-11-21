# GET /api/v1/events

Lista paginada de los eventos del usuario autenticado. No es posible ver eventos de otro usuario.

## Parámetros

| Parámetro | Por defecto | Notas |
|---|---|---|
| `page` | 0 | base 0; un valor negativo se trata como 0 |
| `size` | 20 | se ajusta al rango 1–100 (p. ej. `size=5000` devuelve 100 por página) |

Un valor no numérico (`page=uno`) responde **400** con el formato de [errores](errores.md).
Sin sesión responde **401**.

## Respuesta 200

```json
{
  "content": [ { "id": 7, "mode": "PASSIVE", "distanceCm": 80.0, "createdAt": "..." } ],
  "currentPage": 0, "pageSize": 20, "totalElements": 1,
  "totalPages": 1, "first": true, "last": true
}
```
