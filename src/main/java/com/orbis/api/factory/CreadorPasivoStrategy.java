package com.orbis.api.factory;

import com.orbis.api.command.EjecutorComando;
import com.orbis.api.repository.RegistroEventoRepository;
import com.orbis.api.strategy.ModoProcesamientoStrategy;
import com.orbis.api.strategy.PasivoStrategy;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Patrón Factory Method — Creator concreto para modo Pasivo.
 * Instancia y configura una PasivoStrategy con todas sus dependencias.
 */
@Component
public class CreadorPasivoStrategy extends CreadorStrategy {

    private final RegistroEventoRepository repository;
    private final EjecutorComando ejecutorComando;
    private final ApplicationEventPublisher publisher;

    public CreadorPasivoStrategy(RegistroEventoRepository repository,
                                  EjecutorComando ejecutorComando,
                                  ApplicationEventPublisher publisher) {
        this.repository      = repository;
        this.ejecutorComando = ejecutorComando;
        this.publisher       = publisher;
    }

    @Override
    public ModoProcesamientoStrategy crearEstrategia() {
        return new PasivoStrategy(repository, ejecutorComando, publisher);
    }
}
