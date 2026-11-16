# Guía de prueba física — Logitech C920 en el selector de cámaras

Estado: **guía preparada, prueba no realizada.** Ninguna validación de la C920 se considera hecha hasta que alguien la ejecute con la cámara conectada y anote el resultado. Esta guía no es evidencia.

## Qué se quiere demostrar

1. El panel lista la C920 entre los dispositivos de vídeo.
2. Se puede seleccionar la C920 y la vista en vivo muestra su imagen.
3. Cambiar a otra cámara detiene el análisis en curso y no envía nada con la cámara anterior.
4. Si la C920 se desconecta, el panel muestra un estado recuperable, el botón Reintentar aparece y se puede elegir otra cámara.
5. Al reconectar la C920, la vista vuelve a funcionar sin recargar la página.

## Material

- Logitech C920 (o equivalente UVC).
- Un ordenador con Chrome o Edge actualizado.
- Permiso de cámara concedido al sitio.
- El panel en local (`npm run dev` o servidor estático) apuntando al backend de pruebas. **No usar producción.**
- Registro escrito: fecha, versión del código (commit), navegador, sistema operativo.

## Procedimiento

1. Conectar la C920 antes de abrir la página.
2. Abrir el panel. Iniciar sesión con una cuenta de prueba.
3. Abrir el selector de cámaras. Anotar si aparece "Logitech" o "C920" y cuántas cámaras hay.
4. Seleccionar la C920. Esperar a que la vista muestre imagen. Anotar el tiempo.
5. Pulsar **Describir** (modo manual). Anotar si se envía, el tiempo hasta la respuesta y si aparece el resultado.
6. Activar **Auto**. Dejar 60 segundos. Anotar si hay envíos cada ~5 s y si hay envíos separados por menos de 2 s.
7. Desactivar Auto. Anotar si deja de enviar.
8. Con Auto activo, cambiar a otra cámara si existe. Anotar si se envía algo con la cámara anterior después del cambio.
9. Desconectar la C920 físicamente con Auto activo. Anotar el estado mostrado y si aparece Reintentar.
10. Volver a conectar la C920 y pulsar Reintentar. Anotar si la vista vuelve.
11. Probar la cámara de portátil, si existe, como alternativa.

## Qué anotar y cómo

| Punto | Esperado | Resultado real | Evidencia (captura o tiempo) |
|---|---|---|---|
| Aparece en el selector | Sí | | |
| Muestra imagen | Sí | | |
| Tiempo hasta primera imagen | < 3 s | | |
| Descripción manual | Resultado en pantalla | | |
| Auto: intervalo | ~5 s, sin pares < 2 s | | |
| Cambio de cámara: envíos con la cámara anterior | 0 | | |
| Desconexión: POST tras la desconexión | 0 | | |
| Estado recuperable visible | Sí | | |
| Reintentar aparece | Sí | | |
| Reconexión funciona | Sí | | |

## Criterio de cierre

La validación de la C920 se considera **completa** solo si todos los puntos cumplen lo esperado y el registro incluye evidencia (capturas o tiempos). Un punto fallido se anota como fallo, con la descripción exacta de lo que se vio.

## Lo que esta guía no cubre

- Calidad de imagen para el modelo de IA (no es el mismo objetivo que el selector).
- Comportamiento con la ESP32-CAM (es otro hardware).
- Rendimiento con varias cámaras conectadas a la vez.
