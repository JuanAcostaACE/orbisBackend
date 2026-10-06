package com.smartcane.api.service;

import com.smartcane.api.factory.StrategyFactory;
import com.smartcane.api.model.RegistroEvento;
import com.smartcane.api.repository.RegistroEventoRepository;
import com.smartcane.api.state.ContextoSistema;
import com.smartcane.api.strategy.ModoProcesamientoStrategy;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Servicio central de eventos.
 *
 * Orquesta los patrones de diseño en este orden:
 *   1. State  → actualiza el estado del sistema según el modo del evento
 *   2. Factory → obtiene la strategy correcta via StrategyFactory
 *   3. Strategy → procesa el evento (internamente usa Command + Observer + Facade)
 */
@Service
public class EventoService {

    private final StrategyFactory strategyFactory;
    private final ContextoSistema contextoSistema;
    private final RegistroEventoRepository registroEventoRepository;

    public EventoService(StrategyFactory strategyFactory,
                         ContextoSistema contextoSistema,
                         RegistroEventoRepository registroEventoRepository) {
        this.strategyFactory            = strategyFactory;
        this.contextoSistema            = contextoSistema;
        this.registroEventoRepository   = registroEventoRepository;
    }

    public RegistroEvento gestionarEvento(RegistroEvento request) {
        // 1. State: transicionar al estado correcto según el evento recibido
        contextoSistema.procesarEvento(request);

        // 2. Factory Method: crear la strategy adecuada para el modo
        ModoProcesamientoStrategy estrategia = strategyFactory.obtenerEstrategia(request.getModo());

        // 3. Strategy: ejecutar el procesamiento (Command + Facade + Observer internamente)
        return estrategia.procesar(request);
    }

    public List<RegistroEvento> listarTodos() {
        return registroEventoRepository.findAll();
    }

    public Optional<RegistroEvento> buscarPorId(Long id) {
        return registroEventoRepository.findById(id);
    }

    /** Expone el estado actual del sistema para el controller (útil para debugging). */
    public String getEstadoActual() {
        return contextoSistema.getNombreEstadoActual();
    }
}