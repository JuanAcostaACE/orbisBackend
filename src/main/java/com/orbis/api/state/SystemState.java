package com.orbis.api.state;

import com.orbis.api.model.EventRecord;

/**
 * Patrón State — Interfaz del Estado.
 * Cada estado concreto define cómo reacciona el sistema
 * ante la llegada de un evento del ESP32.
 */
public interface SystemState {

    /**
     * Maneja el evento entrante y puede provocar una transición de estado.
     *
     * @param contexto Contexto compartido que permite cambiar de estado.
     * @param evento   Evento recibido desde el ESP32.
     */
    void handle(SystemContext context, EventRecord event);

    /** Nombre legible del estado actual (para logs y respuestas HTTP). */
    String getName();
}
