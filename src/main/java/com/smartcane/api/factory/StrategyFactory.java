package com.smartcane.api.factory;

import com.smartcane.api.strategy.ModoProcesamientoStrategy;
import org.springframework.stereotype.Component;

/**
 * Patrón Factory Method — Cliente de la fábrica.
 * Usa los creators concretos sin conocer cómo instancian las strategies.
 * EventoService puede usar esta factory como alternativa o complemento
 * a la resolución automática de Spring por Map.
 *
 * Este es el punto de entrada único para obtener cualquier strategy:
 *   strategyFactory.obtenerEstrategia("PASIVO") → PasivoStrategy
 *   strategyFactory.obtenerEstrategia("ACTIVO") → ActivoStrategy
 */
@Component
public class StrategyFactory {

    private final CreadorPasivoStrategy creadorPasivo;
    private final CreadorActivoStrategy creadorActivo;

    public StrategyFactory(CreadorPasivoStrategy creadorPasivo,
                           CreadorActivoStrategy creadorActivo) {
        this.creadorPasivo = creadorPasivo;
        this.creadorActivo = creadorActivo;
    }

    /**
     * Retorna la strategy apropiada para el modo dado.
     * Cada llamada puede crear una nueva instancia (según el creator).
     *
     * @param modo "PASIVO" o "ACTIVO"
     * @return Strategy configurada y lista para procesar
     * @throws IllegalArgumentException si el modo no está soportado
     */
    public ModoProcesamientoStrategy obtenerEstrategia(String modo) {
        if (modo == null) throw new IllegalArgumentException("El modo no puede ser nulo");

        return switch (modo.toUpperCase()) {
            case "PASIVO" -> creadorPasivo.crearEstrategia();
            case "ACTIVO" -> creadorActivo.crearEstrategia();
            default       -> throw new IllegalArgumentException("Modo de procesamiento no soportado: " + modo);
        };
    }
}
