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
 * Patron Strategy - Implementacion Activa.
 *
 * Integracion de patrones:
 *   - Facade:   usa VisionFacade para desacoplarse del proveedor de IA
 *   - Command:  usa ComandoConsultarIA y ComandoGuardarEvento via EjecutorComando
 *   - Observer: publica EventoRegistradoEvent despues de guardar
 *
 * Flujos soportados:
 *   1. Frontend envia etiquetasIA ya calculadas (HF llamado desde el navegador) -> guardar directo
 *   2. Frontend envia imagenUrl en Base64 -> llamar VisionFacade -> guardar
 *   3. Ni imagen ni etiquetas -> guardar con "SIN_IMAGEN"
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

        String etiquetasYaCalculadas = eventoRequest.getEtiquetasIA();
        String imagenBase64          = eventoRequest.getImagenUrl();

        if (etiquetasYaCalculadas != null && !etiquetasYaCalculadas.isBlank()) {
            // Flujo 1: El frontend (navegador) ya llamo a Hugging Face y envio las etiquetas.
            // No es necesario llamar a Vision de nuevo - conservar las etiquetas recibidas.

        } else if (imagenBase64 != null && !imagenBase64.isBlank()) {
            // Flujo 2: El frontend envio la imagen en Base64 para que el backend llame a la IA.
            try {
                ComandoConsultarIA comandoIA = new ComandoConsultarIA(visionFacade, imagenBase64);
                List<String> etiquetas = ejecutorComando.ejecutar(comandoIA);
                eventoRequest.setEtiquetasIA(String.join(", ", etiquetas));
            } catch (VisionException e) {
                eventoRequest.setEtiquetasIA("ERROR_VISION: " + e.getMessage());
            }

        } else {
            // Flujo 3: Sin imagen ni etiquetas.
            eventoRequest.setEtiquetasIA("SIN_IMAGEN");
        }

        // Command: encapsula la persistencia
        ComandoGuardarEvento comandoGuardar = new ComandoGuardarEvento(repository, eventoRequest);
        RegistroEvento eventoGuardado = ejecutorComando.ejecutar(comandoGuardar);

        // Observer: notificar a todos los observers suscritos
        publisher.publishEvent(new EventoRegistradoEvent(this, eventoGuardado));

        return eventoGuardado;
    }
}