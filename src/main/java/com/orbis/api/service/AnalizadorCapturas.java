package com.orbis.api.service;

import com.orbis.api.model.CapturaFotografia;
import com.orbis.api.model.EstadoCaptura;
import com.orbis.api.repository.CapturaFotografiaRepository;
import com.orbis.api.vision.yolo.Deteccion;
import com.orbis.api.vision.yolo.DescriptorEscena;
import com.orbis.api.vision.yolo.DecodificadorImagen;
import com.orbis.api.vision.yolo.DeteccionException;
import com.orbis.api.vision.yolo.DetectorYolox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * Worker de análisis: toma capturas en RECIBIDA y las procesa en UN solo hilo.
 *
 * Flujo: RECIBIDA -> EN_PROCESO -> DESCRITA (descripción y etiquetas) | FALLIDA (motivo).
 * Ninguna excepción deja una captura en EN_PROCESO: cualquier fallo termina en FALLIDA.
 * Los bytes se borran al terminar, pase lo que pase (bloque finally).
 *
 * Arranque: el worker no encola nada hasta ApplicationReadyEvent, para no interferir con
 * RecuperacionCapturas (que marca como FALLIDA las capturas EN_PROCESO de un reinicio).
 * El modelo se carga en la primera inferencia, no durante el arranque.
 */
@Component
public class AnalizadorCapturas {

    private static final Logger log = LoggerFactory.getLogger(AnalizadorCapturas.class);

    private final CapturaFotografiaRepository capturas;
    private final CapturaService capturaService;
    private final DetectorYolox detector;
    private final boolean activo;
    private final ExecutorService ejecutor = Executors.newSingleThreadExecutor(r -> {
        Thread hilo = new Thread(r, "analisis-capturas");
        hilo.setDaemon(true);
        return hilo;
    });
    private final Set<Long> encoladas = ConcurrentHashMap.newKeySet();
    private volatile boolean arrancado;

    public AnalizadorCapturas(CapturaFotografiaRepository capturas,
                              CapturaService capturaService,
                              DetectorYolox detector,
                              @Value("${app.analisis.activo:true}") boolean activo) {
        this.capturas = capturas;
        this.capturaService = capturaService;
        this.detector = detector;
        this.activo = activo;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void alArrancar() {
        arrancado = true;
    }

    @Scheduled(
            initialDelayString = "${app.analisis.retardo-inicial-ms:5000}",
            fixedDelayString = "${app.analisis.intervalo-ms:1000}")
    public void encolarPendientes() {
        if (!activo || !arrancado) {
            return;
        }
        for (CapturaFotografia captura : capturas.findByEstado(EstadoCaptura.RECIBIDA)) {
            Long id = captura.getId();
            if (encoladas.add(id)) {
                try {
                    ejecutor.submit(() -> {
                        try {
                            procesar(id);
                        } finally {
                            encoladas.remove(id);
                        }
                    });
                } catch (RejectedExecutionException e) {
                    encoladas.remove(id);
                }
            }
        }
    }

    /** Procesa una captura. Nunca lanza excepción y nunca la deja en EN_PROCESO. */
    void procesar(Long id) {
        try {
            try {
                capturaService.transicionar(id, EstadoCaptura.EN_PROCESO, null);
            } catch (IllegalStateException | IllegalArgumentException e) {
                return; // ya no está en RECIBIDA (caducada u otra ejecución)
            }

            byte[] bytes = capturaService.contenidoImagenVigente(id).orElse(null);
            if (bytes == null) {
                fallar(id, "IMAGEN_NO_DISPONIBLE");
                return;
            }
            BufferedImage imagen = DecodificadorImagen.decodificar(bytes);
            if (imagen == null) {
                fallar(id, "IMAGEN_NO_DECODIFICABLE");
                return;
            }

            List<Deteccion> detecciones = detector.detectar(imagen);
            DescriptorEscena.Descripcion descripcion = DescriptorEscena.describir(detecciones);
            capturaService.registrarDescripcion(id, descripcion.texto(),
                    String.join(",", descripcion.etiquetas()));
        } catch (DeteccionException e) {
            fallar(id, e.getMotivo());
        } catch (RuntimeException e) {
            log.warn("Error inesperado analizando la captura {}: {}", id, e.getClass().getSimpleName());
            fallar(id, "ERROR_PROCESADO");
        } finally {
            borrarBytes(id);
        }
    }

    private void fallar(Long id, String motivo) {
        try {
            capturaService.transicionar(id, EstadoCaptura.FALLIDA, motivo);
        } catch (RuntimeException e) {
            log.error("No se pudo marcar como FALLIDA la captura {}: {}", id, e.getClass().getSimpleName());
        }
    }

    private void borrarBytes(Long id) {
        try {
            capturaService.borrarImagen(id);
        } catch (RuntimeException e) {
            log.error("No se pudieron borrar los bytes de la captura {}", id);
        }
    }

    @PreDestroy
    public void cerrar() {
        ejecutor.shutdownNow();
    }
}
