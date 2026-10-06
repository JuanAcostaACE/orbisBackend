package com.smartcane.api.state;

import com.smartcane.api.model.RegistroEvento;

/**
 * Patrón State — Interfaz del Estado.
 * Cada estado concreto define cómo reacciona el sistema
 * ante la llegada de un evento del ESP32.
 */
public interface EstadoSistema {

    /**
     * Maneja el evento entrante y puede provocar una transición de estado.
     *
     * @param contexto Contexto compartido que permite cambiar de estado.
     * @param evento   Evento recibido desde el ESP32.
     */
    void manejarEvento(ContextoSistema contexto, RegistroEvento evento);

    /** Nombre legible del estado actual (para logs y respuestas HTTP). */
    String getNombre();
}
