package com.smartcane.api.observer;

/**
 * Patrón Observer — Interfaz del Observer.
 * Define el contrato que todo observer de eventos del SmartCane debe cumplir.
 * Desacopla los observers concretos del sujeto (las strategies).
 */
public interface EventoObserver {
    void onEventoRegistrado(EventoRegistradoEvent evento);
}
