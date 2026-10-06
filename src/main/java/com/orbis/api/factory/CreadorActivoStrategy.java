package com.orbis.api.factory;

import com.orbis.api.command.EjecutorComando;
import com.orbis.api.facade.VisionFacade;
import com.orbis.api.repository.RegistroEventoRepository;
import com.orbis.api.strategy.ActivoStrategy;
import com.orbis.api.strategy.ModoProcesamientoStrategy;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Patrón Factory Method — Creator concreto para modo Activo.
 * Instancia y configura una ActivoStrategy con todas sus dependencias,
 * incluyendo el VisionFacade que conecta con Google Cloud Vision.
 */
@Component
public class CreadorActivoStrategy extends CreadorStrategy {

    private final RegistroEventoRepository repository;
    private final VisionFacade visionFacade;
    private final EjecutorComando ejecutorComando;
    private final ApplicationEventPublisher publisher;

    public CreadorActivoStrategy(RegistroEventoRepository repository,
                                  VisionFacade visionFacade,
                                  EjecutorComando ejecutorComando,
                                  ApplicationEventPublisher publisher) {
        this.repository      = repository;
        this.visionFacade    = visionFacade;
        this.ejecutorComando = ejecutorComando;
        this.publisher       = publisher;
    }

    @Override
    public ModoProcesamientoStrategy crearEstrategia() {
        return new ActivoStrategy(repository, visionFacade, ejecutorComando, publisher);
    }
}
