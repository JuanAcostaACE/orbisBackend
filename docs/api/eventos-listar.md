# GET /api/v1/eventos

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
  "contenido": [ { "id": 7, "modo": "PASIVO", "distanciaCm": 80.0, "fechaHora": "..." } ],
  "paginaActual": 0, "tamanioPagina": 20, "totalElementos": 1,
  "totalPaginas": 1, "primera": true, "ultima": true
}
```
