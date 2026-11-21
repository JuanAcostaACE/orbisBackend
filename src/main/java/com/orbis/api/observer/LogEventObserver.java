package com.orbis.api.observer;

import com.orbis.api.model.EventRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

/**
 * Patrón Observer — Implementación concreta: Logger de eventos.
 * Registra cada evento persistido con su modo, distancia y resultado IA.
 *
 * Implementa tanto EventObserver (interfaz propia) como ApplicationListener
 * (integración con Spring para recibir los eventos publicados).
 */
@Component
public class LogEventObserver implements EventObserver, ApplicationListener<EventRecordedEvent> {

    private static final Logger log = LoggerFactory.getLogger(LogEventObserver.class);

    @Override
    public void onApplicationEvent(EventRecordedEvent applicationEvent) {
        onEventRecorded(applicationEvent);
    }

    @Override
    public void onEventRecorded(EventRecordedEvent event) {
        EventRecord re = event.getEvent();

        String labels = re.getAiLabels() != null ? re.getAiLabels() : "—";

        log.info("[Observer] ✔ Evento #{} | Modo: {} | Distancia: {} cm | IA: {}",
                re.getId(),
                re.getMode(),
                re.getDistanceCm() != null ? re.getDistanceCm() : "N/A",
                labels);
    }
}
