package com.smartcane.api.command;

import com.smartcane.api.model.RegistroEvento;
import com.smartcane.api.repository.RegistroEventoRepository;

/**
 * Patrón Command — Comando concreto: Guardar evento.
 * Encapsula la operación de persistencia de un RegistroEvento.
 * PasivoStrategy lo crea y lo entrega al EjecutorComando sin saber
 * cómo ni cuándo se ejecutará.
 */
public class ComandoGuardarEvento implements Comando<RegistroEvento> {

    private final RegistroEventoRepository repository;
    private final RegistroEvento evento;

    public ComandoGuardarEvento(RegistroEventoRepository repository, RegistroEvento evento) {
        this.repository = repository;
        this.evento = evento;
    }

    @Override
    public RegistroEvento ejecutar() {
        return repository.save(evento);
    }
}
