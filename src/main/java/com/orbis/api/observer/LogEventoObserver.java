package com.orbis.api.observer;

import com.orbis.api.model.RegistroEvento;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

/**
 * Patrón Observer — Implementación concreta: Logger de eventos.
 * Registra cada evento persistido con su modo, distancia y resultado IA.
 *
 * Implementa tanto EventoObserver (interfaz propia) como ApplicationListener
 * (integración con Spring para recibir los eventos publicados).
 */
@Component
public class LogEventoObserver implements EventoObserver, ApplicationListener<EventoRegistradoEvent> {

    private static final Logger log = LoggerFactory.getLogger(LogEventoObserver.class);

    @Override
    public void onApplicationEvent(EventoRegistradoEvent applicationEvent) {
        onEventoRegistrado(applicationEvent);
    }

    @Override
    public void onEventoRegistrado(EventoRegistradoEvent evento) {
        RegistroEvento re = evento.getEvento();

        String etiquetas = re.getEtiquetasIA() != null ? re.getEtiquetasIA() : "—";

        log.info("[Observer] ✔ Evento #{} | Modo: {} | Distancia: {} cm | IA: {}",
                re.getId(),
                re.getModo(),
                re.getDistanciaCm() != null ? re.getDistanciaCm() : "N/A",
                etiquetas);
    }
}
