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
public class EjecutorComando {

    private static final Logger log = LoggerFactory.getLogger(EjecutorComando.class);

    public <T> T ejecutar(Comando<T> comando) {
        log.debug("[Command] Ejecutando: {}", comando.getClass().getSimpleName());
        T resultado = comando.ejecutar();
        log.debug("[Command] Completado: {}", comando.getClass().getSimpleName());
        return resultado;
    }
}
