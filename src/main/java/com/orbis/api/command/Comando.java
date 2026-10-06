package com.orbis.api.command;

/**
 * Patrón Command — Interfaz genérica del Command.
 * Encapsula una acción como objeto, permitiendo parametrizar,
 * encolar o deshacer operaciones independientemente de quien las invoca.
 *
 * @param <T> Tipo del resultado que retorna el comando al ejecutarse.
 */
public interface Comando<T> {
    T ejecutar();
}
