package com.orbis.api.strategy;

import com.orbis.api.command.ComandoGuardarEvento;
import com.orbis.api.command.EjecutorComando;
import com.orbis.api.model.RegistroEvento;
import com.orbis.api.observer.EventoRegistradoEvent;
import com.orbis.api.repository.RegistroEventoRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Patrón Strategy — Implementación Pasiva.
 * Responsabilidad: Guardar telemetría de obstáculo sin consultar IA.
 *
 * Integración de patrones:
 *   - Command: delega la persistencia a ComandoGuardarEvento
 *   - Observer: publica EventoRegistradoEvent después de guardar
 */
@Component("PASIVO")
public class PasivoStrategy implements ModoProcesamientoStrategy {

    private final RegistroEventoRepository repository;
    private final EjecutorComando ejecutorComando;
    private final ApplicationEventPublisher publisher;

    public PasivoStrategy(RegistroEventoRepository repository,
                          EjecutorComando ejecutorComando,
                          ApplicationEventPublisher publisher) {
        this.repository      = repository;
        this.ejecutorComando = ejecutorComando;
        this.publisher       = publisher;
    }

    @Override
    public RegistroEvento procesar(RegistroEvento eventoRequest) {
        // Modo pasivo: 100% local — solo guarda telemetría, sin IA
        ComandoGuardarEvento comando = new ComandoGuardarEvento(repository, eventoRequest);
        RegistroEvento eventoGuardado = ejecutorComando.ejecutar(comando);

        // Notificar observers (ej: LogEventoObserver)
        publisher.publishEvent(new EventoRegistradoEvent(this, eventoGuardado));

        return eventoGuardado;
    }
}