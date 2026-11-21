package com.orbis.api.strategy;

import com.orbis.api.command.SaveEventCommand;
import com.orbis.api.command.CommandExecutor;
import com.orbis.api.model.EventRecord;
import com.orbis.api.observer.EventRecordedEvent;
import com.orbis.api.repository.EventRecordRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Patrón Strategy — Implementación Pasiva.
 * Responsabilidad: Guardar telemetría de obstáculo sin consultar IA.
 *
 * Integración de patrones:
 *   - Command: delega la persistencia a SaveEventCommand
 *   - Observer: publica EventRecordedEvent después de guardar
 */
@Component("PASSIVE")
public class PassiveStrategy implements ProcessingModeStrategy {

    private final EventRecordRepository repository;
    private final CommandExecutor commandExecutor;
    private final ApplicationEventPublisher publisher;

    public PassiveStrategy(EventRecordRepository repository,
                          CommandExecutor commandExecutor,
                          ApplicationEventPublisher publisher) {
        this.repository      = repository;
        this.commandExecutor = commandExecutor;
        this.publisher       = publisher;
    }

    @Override
    public EventRecord process(EventRecord eventRequest) {
        // Modo pasivo: 100% local — solo guarda telemetría, sin IA
        SaveEventCommand command = new SaveEventCommand(repository, eventRequest);
        EventRecord savedEvent = commandExecutor.execute(command);

        // Notificar observers (ej: LogEventObserver)
        publisher.publishEvent(new EventRecordedEvent(this, savedEvent));

        return savedEvent;
    }
}