package com.orbis.api.command;

import com.orbis.api.model.EventRecord;
import com.orbis.api.repository.EventRecordRepository;

/**
 * Patrón Command — Comando concreto: Guardar evento.
 * Encapsula la operación de persistencia de un EventRecord.
 * PassiveStrategy lo crea y lo entrega al CommandExecutor sin saber
 * cómo ni cuándo se ejecutará.
 */
public class SaveEventCommand implements Command<EventRecord> {

    private final EventRecordRepository repository;
    private final EventRecord event;

    public SaveEventCommand(EventRecordRepository repository, EventRecord event) {
        this.repository = repository;
        this.event = event;
    }

    @Override
    public EventRecord execute() {
        return repository.save(event);
    }
}
