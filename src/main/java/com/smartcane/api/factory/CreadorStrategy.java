package com.smartcane.api.factory;

import com.smartcane.api.model.RegistroEvento;
import com.smartcane.api.strategy.ModoProcesamientoStrategy;

/**
 * Patrón Factory Method — Creator abstracto.
 * Define el factory method crearEstrategia() que las subclases deben implementar.
 * También provee un template method procesarConEstrategiaCreada() que usa
 * el factory method para procesar un evento.
 *
 * Relación con Strategy:
 *   Los creators son responsables de instanciar las strategies concretas.
 *   El cliente (StrategyFactory) solo conoce esta abstracción.
 */
public abstract class CreadorStrategy {

    /**
     * Factory Method — debe ser implementado por cada subclase concreta.
     * Retorna la Strategy apropiada para el modo de operación.
     */
    public abstract ModoProcesamientoStrategy crearEstrategia();

    /**
     * Template Method que usa el Factory Method.
     * Permite procesar un evento usando la estrategia creada por esta fábrica.
     */
    public RegistroEvento procesarConEstrategiaCreada(RegistroEvento evento) {
        ModoProcesamientoStrategy estrategia = crearEstrategia();
        return estrategia.procesar(evento);
    }
}
