package com.orbis.api.factory;

import com.orbis.api.command.CommandExecutor;
import com.orbis.api.facade.VisionFacade;
import com.orbis.api.repository.EventRecordRepository;
import com.orbis.api.strategy.ActiveStrategy;
import com.orbis.api.strategy.ProcessingModeStrategy;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Patrón Factory Method — Creator concreto para modo Activo.
 * Instancia y configura una ActiveStrategy con todas sus dependencias,
 * incluyendo el VisionFacade que conecta con Google Cloud Vision.
 */
@Component
public class ActiveStrategyCreator extends StrategyCreator {

    private final EventRecordRepository repository;
    private final VisionFacade visionFacade;
    private final CommandExecutor commandExecutor;
    private final ApplicationEventPublisher publisher;

    public ActiveStrategyCreator(EventRecordRepository repository,
                                  VisionFacade visionFacade,
                                  CommandExecutor commandExecutor,
                                  ApplicationEventPublisher publisher) {
        this.repository      = repository;
        this.visionFacade    = visionFacade;
        this.commandExecutor = commandExecutor;
        this.publisher       = publisher;
    }

    @Override
    public ProcessingModeStrategy createStrategy() {
        return new ActiveStrategy(repository, visionFacade, commandExecutor, publisher);
    }
}
