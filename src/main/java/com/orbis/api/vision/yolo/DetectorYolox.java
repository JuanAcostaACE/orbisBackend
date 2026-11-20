package com.orbis.api.vision.yolo;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.util.List;
import java.util.Map;

/**
 * Inferencia YOLOX-Nano con ONNX Runtime (CPU).
 *
 * El modelo se carga de forma perezosa, en la primera inferencia, para no bloquear el arranque.
 * Si la carga falla, cada inferencia lanza DeteccionException("IA_NO_DISPONIBLE").
 * Los bytes de la imagen nunca se registran en logs.
 */
@Component
public class DetectorYolox {

    private static final Logger log = LoggerFactory.getLogger(DetectorYolox.class);
    static final String RECURSO_MODELO = "models/yolox_nano.onnx";
    private static final int NUM_CLASES = ClaseCoco.values().length;

    private final Object cargaLock = new Object();
    private volatile OrtSession sesion;
    private String nombreEntrada;

    /** Devuelve las detecciones con confianza >= 0,45 tras NMS (IoU 0,45). Puede estar vacía. */
    public List<Deteccion> detectar(BufferedImage imagen) {
        OrtSession s = sesion();
        PreprocesadorYolox.Entrada entrada = PreprocesadorYolox.letterbox(imagen);
        int tam = PostprocesadorYolox.TAMANO_ENTRADA;
        try (OnnxTensor tensor = OnnxTensor.createTensor(
                OrtEnvironment.getEnvironment(),
                FloatBuffer.wrap(entrada.datos()),
                new long[]{1, 3, tam, tam});
             OrtSession.Result resultado = s.run(Map.of(nombreEntrada, tensor))) {
            OnnxTensor salida = (OnnxTensor) resultado.get(0);
            FloatBuffer buffer = salida.getFloatBuffer();
            float[] plano = new float[buffer.remaining()];
            buffer.get(plano);
            return PostprocesadorYolox.decodificar(plano, tam, NUM_CLASES, entrada.ratio(),
                    imagen.getWidth(), imagen.getHeight(),
                    PostprocesadorYolox.UMBRAL_CONFIANZA, PostprocesadorYolox.UMBRAL_IOU);
        } catch (OrtException | RuntimeException e) {
            log.warn("Fallo de inferencia YOLOX: {}", e.getClass().getSimpleName());
            throw new DeteccionException("ERROR_INFERENCIA", e);
        }
    }

    private OrtSession sesion() {
        OrtSession actual = sesion;
        if (actual != null) {
            return actual;
        }
        synchronized (cargaLock) {
            if (sesion != null) {
                return sesion;
            }
            try (InputStream in = DetectorYolox.class.getClassLoader().getResourceAsStream(RECURSO_MODELO)) {
                if (in == null) {
                    throw new IOException("Recurso de modelo no encontrado: " + RECURSO_MODELO);
                }
                byte[] modelo = in.readAllBytes();
                OrtEnvironment entorno = OrtEnvironment.getEnvironment();
                try (OrtSession.SessionOptions opciones = new OrtSession.SessionOptions()) {
                    opciones.setIntraOpNumThreads(1);
                    sesion = entorno.createSession(modelo, opciones);
                }
                nombreEntrada = sesion.getInputNames().iterator().next();
                log.info("Modelo YOLOX-Nano cargado");
                return sesion;
            } catch (OrtException | IOException | RuntimeException e) {
                log.error("No se pudo cargar el modelo YOLOX-Nano: {}", e.getClass().getSimpleName());
                throw new DeteccionException("IA_NO_DISPONIBLE", e);
            }
        }
    }

    @PreDestroy
    public void cerrar() {
        synchronized (cargaLock) {
            if (sesion != null) {
                try {
                    sesion.close();
                } catch (OrtException e) {
                    log.warn("Error al cerrar la sesión YOLOX: {}", e.getClass().getSimpleName());
                }
                sesion = null;
            }
        }
    }
}
