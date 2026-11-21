package com.orbis.api.state;

import com.orbis.api.model.EventRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Patrón State — Estado Activo.
 * El sistema está procesando la imagen con Google Vision.
 * Transición:
 *   - Siempre vuelve a InactiveState después de procesar (el análisis es puntual).
 */
@Component
public class ActiveState implements SystemState {

    private static final Logger log = LoggerFactory.getLogger(ActiveState.class);

    @Override
    public void handle(SystemContext context, EventRecord event) {
        log.info("[State] Activo → Inactivo (análisis completado para evento modo={})", event.getMode());
        // Después de analizar, el sistema siempre vuelve a reposo
        context.setState(context.getInactiveState());
    }

    @Override
    public String getName() {
        return "ACTIVE";
    }
}
