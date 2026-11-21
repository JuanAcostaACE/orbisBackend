package com.orbis.api.strategy;

import com.orbis.api.command.QueryAiCommand;
import com.orbis.api.command.SaveEventCommand;
import com.orbis.api.command.CommandExecutor;
import com.orbis.api.facade.VisionException;
import com.orbis.api.facade.VisionFacade;
import com.orbis.api.model.EventRecord;
import com.orbis.api.observer.EventRecordedEvent;
import com.orbis.api.repository.EventRecordRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Patron Strategy - Implementacion Activa.
 *
 * Integracion de patrones:
 *   - Facade:   usa VisionFacade para desacoplarse del proveedor de IA
 *   - Command:  usa QueryAiCommand y SaveEventCommand via CommandExecutor
 *   - Observer: publica EventRecordedEvent despues de guardar
 *
 * Flujos soportados:
 *   1. Frontend envia aiLabels ya calculadas (HF llamado desde el navegador) -> guardar directo
 *   2. Frontend envia imageUrl en Base64 -> llamar VisionFacade -> guardar
 *   3. Ni imagen ni etiquetas -> guardar con "SIN_IMAGEN"
 */
@Component("ACTIVE")
public class ActiveStrategy implements ProcessingModeStrategy {

    private final EventRecordRepository repository;
    private final VisionFacade visionFacade;
    private final CommandExecutor commandExecutor;
    private final ApplicationEventPublisher publisher;

    public ActiveStrategy(EventRecordRepository repository,
                          VisionFacade visionFacade,
                          CommandExecutor commandExecutor,
                          ApplicationEventPublisher publisher) {
        this.repository      = repository;
        this.visionFacade    = visionFacade;
        this.commandExecutor = commandExecutor;
        this.publisher       = publisher;
    }

    @Override
    public EventRecord process(EventRecord eventRequest) {

        String precomputedLabels = eventRequest.getAiLabels();
        String imageBase64          = eventRequest.getImageUrl();

        if (precomputedLabels != null && !precomputedLabels.isBlank()) {
            // Flujo 1: El frontend (GestorVision/COCO-SSD o MobileNet) ya calculó las etiquetas.
            // Se pasan por VisionAdapter para validarlas y normalizarlas antes de persistir.
            // Esto garantiza que el Adapter/Facade tiene responsabilidad REAL en el pipeline.
            try {
                List<String> normalizedLabels = visionFacade.analyzeImage(precomputedLabels);
                eventRequest.setAiLabels(String.join(", ", normalizedLabels));
            } catch (VisionException e) {
                // Si la validación falla, se registra como error de IA pero no se rechaza el evento
                eventRequest.setAiLabels("ERROR_VISION: " + e.getMessage());
            }
        } else if (imageBase64 != null && !imageBase64.isBlank()) {
            // Flujo 2: El frontend envio la imagen en Base64 para que el backend llame a la IA.
            try {
                QueryAiCommand aiCommand = new QueryAiCommand(visionFacade, imageBase64);
                List<String> labels = commandExecutor.execute(aiCommand);
                eventRequest.setAiLabels(String.join(", ", labels));
            } catch (VisionException e) {
                eventRequest.setAiLabels("ERROR_VISION: " + e.getMessage());
            }

        } else {
            // Flujo 3: Sin imagen ni etiquetas.
            eventRequest.setAiLabels("NO_IMAGE");
        }

        // Command: encapsula la persistencia
        SaveEventCommand saveCommand = new SaveEventCommand(repository, eventRequest);
        EventRecord savedEvent = commandExecutor.execute(saveCommand);

        // Observer: notificar a todos los observers suscritos
        publisher.publishEvent(new EventRecordedEvent(this, savedEvent));

        return savedEvent;
    }
}