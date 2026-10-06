package com.orbis.api.observer;

import com.orbis.api.model.RegistroEvento;
import org.springframework.context.ApplicationEvent;

/**
 * Patrón Observer — Evento de dominio.
 * Se publica cada vez que una Strategy persiste un RegistroEvento.
 * Los Observers (ApplicationListener) reaccionan sin acoplarse a las strategies.
 */
public class EventoRegistradoEvent extends ApplicationEvent {

    private final RegistroEvento evento;

    public EventoRegistradoEvent(Object source, RegistroEvento evento) {
        super(source);
        this.evento = evento;
    }

    public RegistroEvento getEvento() {
        return evento;
    }
}
