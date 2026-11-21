package com.orbis.api.factory;

import com.orbis.api.command.CommandExecutor;
import com.orbis.api.repository.EventRecordRepository;
import com.orbis.api.strategy.ProcessingModeStrategy;
import com.orbis.api.strategy.PassiveStrategy;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Patrón Factory Method — Creator concreto para modo Pasivo.
 * Instancia y configura una PassiveStrategy con todas sus dependencias.
 */
@Component
public class PassiveStrategyCreator extends StrategyCreator {

    private final EventRecordRepository repository;
    private final CommandExecutor commandExecutor;
    private final ApplicationEventPublisher publisher;

    public PassiveStrategyCreator(EventRecordRepository repository,
                                  CommandExecutor commandExecutor,
                                  ApplicationEventPublisher publisher) {
        this.repository      = repository;
        this.commandExecutor = commandExecutor;
        this.publisher       = publisher;
    }

    @Override
    public ProcessingModeStrategy createStrategy() {
        return new PassiveStrategy(repository, commandExecutor, publisher);
    }
}
