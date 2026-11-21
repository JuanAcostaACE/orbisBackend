package com.orbis.api.factory;

import com.orbis.api.strategy.ProcessingModeStrategy;
import org.springframework.stereotype.Component;

/**
 * Patrón Factory Method — Cliente de la fábrica.
 * Usa los creators concretos sin conocer cómo instancian las strategies.
 * EventService puede usar esta factory como alternativa o complemento
 * a la resolución automática de Spring por Map.
 *
 * Este es el punto de entrada único para obtener cualquier strategy:
 *   strategyFactory.getStrategy("PASSIVE") → PassiveStrategy
 *   strategyFactory.getStrategy("ACTIVE") → ActiveStrategy
 */
@Component
public class StrategyFactory {

    private final PassiveStrategyCreator passiveCreator;
    private final ActiveStrategyCreator activeCreator;

    public StrategyFactory(PassiveStrategyCreator passiveCreator,
                           ActiveStrategyCreator activeCreator) {
        this.passiveCreator = passiveCreator;
        this.activeCreator = activeCreator;
    }

    /**
     * Retorna la strategy apropiada para el modo dado.
     * Cada llamada puede crear una nueva instancia (según el creator).
     *
     * @param modo "PASSIVE" o "ACTIVE"
     * @return Strategy configurada y lista para procesar
     * @throws IllegalArgumentException si el modo no está soportado
     */
    public ProcessingModeStrategy getStrategy(String mode) {
        if (mode == null) throw new IllegalArgumentException("El modo no puede ser nulo");

        return switch (mode.toUpperCase()) {
            case "PASSIVE" -> passiveCreator.createStrategy();
            case "ACTIVE" -> activeCreator.createStrategy();
            default       -> throw new IllegalArgumentException("Modo de procesamiento no soportado: " + mode);
        };
    }
}
