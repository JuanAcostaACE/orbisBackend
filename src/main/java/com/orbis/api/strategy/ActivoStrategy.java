package com.orbis.api.strategy;

import com.orbis.api.command.ComandoConsultarIA;
import com.orbis.api.command.ComandoGuardarEvento;
import com.orbis.api.command.EjecutorComando;
import com.orbis.api.facade.VisionException;
import com.orbis.api.facade.VisionFacade;
import com.orbis.api.model.RegistroEvento;
import com.orbis.api.observer.EventoRegistradoEvent;
import com.orbis.api.repository.RegistroEventoRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Patrón Strategy — Implementación Activa.
 * Responsabilidad: Consultar Google Vision y guardar el resultado.
 *
 * Integración de patrones:
 *   - Facade:   usa VisionFacade para desacoplarse de Google Cloud Vision
 *   - Command:  usa ComandoConsultarIA y ComandoGuardarEvento via EjecutorComando
 *   - Observer: publica EventoRegistradoEvent después de guardar
 */
@Component("ACTIVO")
public class ActivoStrategy implements ModoProcesamientoStrategy {

    private final RegistroEventoRepository repository;
    private final VisionFacade visionFacade;
    private final EjecutorComando ejecutorComando;
    private final ApplicationEventPublisher publisher;

    public ActivoStrategy(RegistroEventoRepository repository,
                          VisionFacade visionFacade,
                          EjecutorComando ejecutorComando,
                          ApplicationEventPublisher publisher) {
        this.repository      = repository;
        this.visionFacade    = visionFacade;
        this.ejecutorComando = ejecutorComando;
        this.publisher       = publisher;
    }

    @Override
    public RegistroEvento procesar(RegistroEvento eventoRequest) {
        String imagenBase64 = eventoRequest.getImagenUrl();

        if (imagenBase64 == null || imagenBase64.isBlank()) {
            eventoRequest.setEtiquetasIA("SIN_IMAGEN");
        } else {
            try {
                // Command: encapsula la llamada a Vision
                ComandoConsultarIA comandoIA = new ComandoConsultarIA(visionFacade, imagenBase64);
                List<String> etiquetas = ejecutorComando.ejecutar(comandoIA);
                eventoRequest.setEtiquetasIA(String.join(", ", etiquetas));

            } catch (VisionException e) {
                // Degradación elegante: persistir con error en lugar de perder el evento
                eventoRequest.setEtiquetasIA("ERROR_VISION: " + e.getMessage());
            }
        }

        // Command: encapsula la persistencia
        ComandoGuardarEvento comandoGuardar = new ComandoGuardarEvento(repository, eventoRequest);
        RegistroEvento eventoGuardado = ejecutorComando.ejecutar(comandoGuardar);

        // Observer: notificar a todos los observers suscritos
        publisher.publishEvent(new EventoRegistradoEvent(this, eventoGuardado));

        return eventoGuardado;
    }
}