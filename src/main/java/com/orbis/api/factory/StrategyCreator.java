package com.orbis.api.factory;

import com.orbis.api.model.EventRecord;
import com.orbis.api.strategy.ProcessingModeStrategy;

/**
 * Patrón Factory Method — Creator abstracto.
 * Define el factory method createStrategy() que las subclases deben implementar.
 * También provee un template method processWithCreatedStrategy() que usa
 * el factory method para procesar un evento.
 *
 * Relación con Strategy:
 *   Los creators son responsables de instanciar las strategies concretas.
 *   El cliente (StrategyFactory) solo conoce esta abstracción.
 */
public abstract class StrategyCreator {

    /**
     * Factory Method — debe ser implementado por cada subclase concreta.
     * Retorna la Strategy apropiada para el modo de operación.
     */
    public abstract ProcessingModeStrategy createStrategy();

    /**
     * Template Method que usa el Factory Method.
     * Permite procesar un evento usando la estrategia creada por esta fábrica.
     */
    public EventRecord processWithCreatedStrategy(EventRecord event) {
        ProcessingModeStrategy strategy = createStrategy();
        return strategy.process(event);
    }
}
