# GET /api/v1/eventos

Lista paginada de los eventos del usuario autenticado. No es posible ver eventos de otro usuario.

## Parámetros

| Parámetro | Por defecto | Notas |
|---|---|---|
| `page` | 0 | base 0 |
| `size` | 20 | máximo 100 |

## Respuesta 200

```json
{
  "contenido": [ { "id": 7, "modo": "PASIVO", "distanciaCm": 80.0, "fechaHora": "..." } ],
  "paginaActual": 0, "tamanioPagina": 20, "totalElementos": 1,
  "totalPaginas": 1, "primera": true, "ultima": true
}
```
