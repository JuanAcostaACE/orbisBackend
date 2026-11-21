package com.orbis.api.state;

import com.orbis.api.model.EventRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Patrón State — Estado Inactivo.
 * Estado inicial del sistema cuando no hay obstáculo detectado.
 * Transiciones:
 *   - Recibe PASSIVE → pasa a PassiveState (obstáculo detectado)
 *   - Recibe ACTIVE → pasa a ActiveState (análisis solicitado)
 */
@Component
public class InactiveState implements SystemState {

    private static final Logger log = LoggerFactory.getLogger(InactiveState.class);

    @Override
    public void handle(SystemContext context, EventRecord event) {
        String mode = event.getMode() != null ? event.getMode().toUpperCase() : "";
        switch (mode) {
            case "PASSIVE" -> {
                log.info("[State] Inactivo → Pasivo (obstáculo a {} cm)", event.getDistanceCm());
                context.setState(context.getPassiveState());
            }
            case "ACTIVE" -> {
                log.info("[State] Inactivo → Activo (análisis solicitado)");
                context.setState(context.getActiveState());
            }
            default -> log.warn("[State] InactiveState: modo desconocido '{}'", event.getMode());
        }
    }

    @Override
    public String getName() {
        return "INACTIVE";
    }
}
