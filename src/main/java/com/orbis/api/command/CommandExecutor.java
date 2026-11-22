package com.orbis.api.command;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Patrón Command — Invoker (Ejecutor).
 * Ejecuta cualquier Comando sin conocer su implementación concreta.
 * Punto central donde se puede agregar: logging, retry, timeout, o historial.
 */
@Component
public class CommandExecutor {

    private static final Logger log = LoggerFactory.getLogger(CommandExecutor.class);

    public <T> T execute(Command<T> command) {
        log.debug("[Command] Ejecutando: {}", command.getClass().getSimpleName());
        T outcome = command.execute();
        log.debug("[Command] Completado: {}", command.getClass().getSimpleName());
        return outcome;
    }
}
