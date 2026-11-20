# ORBIS — Evaluación de IA

Estado: **evaluación cualitativa y marco de decisión**. No hay benchmarks propios de ORBIS sobre imágenes de bastón. Las cifras de precisión, latencia o coste que aparezcan en otros documentos del proyecto (por ejemplo `bench/`) pertenecen a conjuntos públicos o a experimentos aislados, y no sustituyen a una medición en condiciones reales.

## Lo que ORBIS necesita

Hay dos capacidades distintas. No son la misma cosa:

| Capacidad | Pregunta que responde | Ejemplo |
|---|---|---|
| Detección de objetos | ¿Qué objetos hay y dónde están en la imagen? | "persona, puerta, silla" con cajas |
| Descripción de escena | ¿Qué describe la imagen en lenguaje natural? | "Hay una puerta delante y una persona a tu derecha." |
| Evaluación de peligro | ¿Este objeto representa un riesgo para la persona? | No la responde ningún modelo de detección por sí solo |

Detectar un objeto **no** permite afirmar su distancia, su posición física ni si es peligroso. La evaluación de peligro necesita reglas, sensores o validación humana, y no se debe presentar como una capacidad del modelo.

## Restricciones

- Descripciones en **español**.
- Latencia aceptable para la persona: la propuesta de `ORBIS_DEVICE_PROTOCOL.md` fija un objetivo de respuesta inmediata de "recibida" y un tiempo de análisis que debe medirse.
- Sin pagos recurrentes ni contratación sin autorización del propietario.
- Privacidad: las imágenes de entornos reales no deben enviarse a terceros sin decisión explícita (ver `ORBIS_SECURITY_AND_PRIVACY.md`).

## Opciones y su situación

| Opción | Tipo | Ventajas | Inconvenientes | Estado |
|---|---|---|---|---|
| COCO-SSD (TensorFlow.js, navegador) | Detección | Ya integrado en el panel; sin coste | Solo detección de 80 clases; bloquea el hilo principal al ejecutarse; no describe escenas | Usado en el panel (experimental) |
| YOLOX-Nano / YOLOX-S (ONNX Runtime) | Detección | Ligero | Licencia de pesos sin verificar; solo detección | Experimento aislado, no integrado |
| Modelo de descripción de escena local (VLM pequeño) | Descripción | Describe en lenguaje natural | Requiere GPU o mucha CPU; calidad en español no verificada | **No evaluado** |
| API de visión en la nube | Descripción | Mejor calidad probable | Coste por petición; envío de imágenes a terceros; dependencia externa | **No contratada** (requiere autorización) |

Cifras de precisión, recall o latencia de estos modelos **no están verificadas** en este documento. Antes de elegir, hay que medirlas con un conjunto de imágenes propio de bastón, con criterio de aceptación escrito de antemano.

## Protocolo de evaluación propuesto

1. Reunir un conjunto de imágenes tomadas con el módulo real (o una cámara de características similares), con anotación de lo que realmente hay.
2. Definir métricas antes de medir: porcentaje de descripciones correctas, falsos positivos ("hay una puerta" sin puerta), omisiones relevantes, tiempo hasta resultado.
3. Medir cada opción sobre el mismo conjunto, en el mismo hardware, con tiempos registrados.
4. Revisar licencia y condiciones de uso de cada modelo y de sus pesos.
5. Documentar el resultado con la fecha, la versión del modelo y el conjunto usado.

## Integración recomendada (desacoplada)

El backend no debe depender de un modelo concreto. Propuesta de interfaz:

```
interface AnalizadorEscena {
    ResultadoAnalisis analizar(byte[] jpeg, String idioma);
}
```

- Una implementación por proveedor o modelo.
- El worker llama a la interfaz, nunca al modelo directamente.
- Un resultado sin detecciones fiables se guarda como `SIN_HALLAZGOS_CONFIABLES`, nunca como "camino despejado".

## Decisión pendiente

Qué modelo usar y dónde ejecutarlo (servidor o teléfono) no está decidido. Requiere la evaluación anterior y una decisión del propietario sobre coste y privacidad.
