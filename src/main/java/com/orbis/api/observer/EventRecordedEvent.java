package com.orbis.api.observer;

import com.orbis.api.model.EventRecord;
import org.springframework.context.ApplicationEvent;

/**
 * Patrón Observer — Evento de dominio.
 * Se publica cada vez que una Strategy persiste un EventRecord.
 * Los Observers (ApplicationListener) reaccionan sin acoplarse a las strategies.
 */
public class EventRecordedEvent extends ApplicationEvent {

    private final EventRecord event;

    public EventRecordedEvent(Object source, EventRecord event) {
        super(source);
        this.event = event;
    }

    public EventRecord getEvent() {
        return event;
    }
}
