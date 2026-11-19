# Analizador de capturas con YOLOX-Nano

Las fotos que llegan del bastón (`POST /api/v1/dispositivos/capturas`, estado `RECIBIDA`) se analizan
con un modelo de detección de objetos. El resultado pasa a `DESCRITA` (con descripción en español) o a
`FALLIDA` (con motivo). Este análisis no es una alerta de seguridad.

## Modelo

- Modelo: YOLOX-Nano, entrada 416x416, formato ONNX (`src/main/resources/models/yolox_nano.onnx`).
- Tamaño: 3 659 407 bytes.
- SHA-256: `c789161ed43c8269fcd4e67c67eeeb4e80c622da2eb296a20bc6007bd18a0b7d`
  (coincide con las copias de `bench-data` y `frontend-camera`).
- Clases: las 80 de COCO, con nombre en español (`ClaseCoco`).
- Licencia del código de YOLOX (Megvii-BaseDetection/YOLOX): Apache-2.0, confirmada en su `LICENSE`.
- Licencia de los pesos: NO CONFIRMADA. El README oficial no declara una licencia separada para
  `yolox_nano.pth`. El `.onnx` de este repo procede de esos pesos, pero su origen exacto no está verificado.
  Antes de un uso comercial hay que confirmarlo con los autores.

## Runtime

- `com.microsoft.onnxruntime:onnxruntime` **1.31.1**, licencia MIT.
- Se eligió 1.31.1 porque es la versión estable más reciente con más de 2 semanas en Maven Central
  (publicada el 26-oct-2026). La 1.32.0 se publicó el 13-nov-2026 y no cumple esa regla.
- Inferencia solo en CPU, un hilo (`intraOpNumThreads=1`).

## Pipeline

1. Preprocesado (`PreprocesadorYolox`): letterbox arriba-izquierda sobre lienzo 416x416 con relleno 114,
   canales BGR (convención cv2 de YOLOX), NCHW float32 en 0-255, sin media ni desviación.
   El redimensionado usa Graphics2D bilineal; no es bit a bit igual a `cv2.resize`.
2. Inferencia (`DetectorYolox`): carga perezosa del modelo en la primera captura, no en el arranque.
3. Postprocesado (`PostprocesadorYolox`): decodificación grid + stride 8/16/32 (convención de
   `demo_postprocess`), score = objectness x clase, umbral de confianza **0,45**, NMS por clase con
   IoU **0,45**.
4. Descripción (`DescriptorEscena`): reglas fijas. Sin detecciones, la frase es
   "No se han identificado objetos con seguridad en la imagen." Nunca se afirma "camino despejado".
   Ejemplo: "Hay una persona y un coche."

## Worker y estados

- `AnalizadorCapturas`: un hilo (`ExecutorService` de 1). Revisa cada segundo las capturas `RECIBIDA`.
- `RECIBIDA` -> `EN_PROCESO` -> `DESCRITA` | `FALLIDA`. Cualquier excepción termina en `FALLIDA`;
  ninguna captura queda en `EN_PROCESO`.
- Motivos de fallo: `IMAGEN_NO_DISPONIBLE`, `IMAGEN_NO_DECODIFICABLE`, `IA_NO_DISPONIBLE` (el modelo no
  carga), `ERROR_INFERENCIA`, `ERROR_PROCESADO`, `IMAGEN_CADUCADA`, y `REINICIO_DURANTE_PROCESO` (de
  `RecuperacionCapturas`, que sigue igual).
- El worker no encola nada hasta `ApplicationReadyEvent`, para no interferir con `RecuperacionCapturas`.
- Propiedades: `app.analisis.activo` (true), `app.analisis.intervalo-ms` (1000),
  `app.analisis.retardo-inicial-ms` (5000), `app.analisis.limpieza-ms` (300000),
  `app.analisis.retencion-horas` (24).

## Retención de imágenes

- Los bytes JPEG viven en la tabla `capturas_imagen` **solo durante el análisis**.
- Se borran al terminar (`DESCRITA` o `FALLIDA`), en un bloque `finally`.
- Si la captura lleva más de 24 h sin procesar, `RetencionImagenes` borra los bytes. Se ejecuta cada
  5 min, así que una imagen puede sobrevivir hasta 24 h y 5 min. El worker nunca usa bytes de más de 24 h.
  Si la captura seguía en `RECIBIDA`, pasa a `FALLIDA` con `IMAGEN_CADUCADA`.
- Los bytes nunca van a logs ni a la API.

## Contrato del endpoint

`GET /api/v1/dispositivos/capturas/{captureId}` añade campos de forma aditiva:

- `descripcion`: texto, o `null` hasta `DESCRITA`.
- `etiquetas`: lista de etiquetas en español, vacía si no hay detecciones.
- `motivoFallo`: motivo cuando `FALLIDA`, `null` en otro caso.

Los campos `captureId`, `estado` y `duplicada` no cambian. `POST` no cambia.

## Medición

- Entorno: CPU de desarrollo (Windows 10, JDK 21), entrada 640x480 reescalada a 416, media de 20 ejecuciones
  tras calentamiento.
- Primera inferencia (incluye carga del modelo): 108 ms.
- Media por inferencia: 35,7 ms.

## Límites conocidos

- COCO no tiene "puerta" ni "escalera". Esas clases no se detectan.
- No hay medición de distancia ni de profundidad. La descripción no indica posición.
- El análisis no es una alerta de seguridad ni sustituye al bastón.
- La descripción solo recoge objetos que COCO conoce; con poca luz o con objetos fuera de esas clases,
  la respuesta será "No se han identificado objetos con seguridad en la imagen.".
- Los tests no verifican con una foto real de persona; solo verifican que el pipeline funciona sin
  excepción y que el postprocesado es correcto.
- Producción usa `ddl-auto: update`: la tabla `capturas_imagen` y las columnas nuevas las crea Hibernate.
  No se ha añadido SQL de migración.
