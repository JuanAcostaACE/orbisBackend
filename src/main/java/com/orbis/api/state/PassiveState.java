package com.orbis.api.state;

import com.orbis.api.model.EventRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Patrón State — Estado Pasivo.
 * El sistema detectó un obstáculo y está vibrando.
 * Transiciones:
 *   - Recibe ACTIVE → pasa a ActiveState (usuario presionó el botón)
 *   - Recibe PASSIVE → permanece (sigue detectando obstáculo)
 *   - Distancia > umbral → vuelve a InactiveState
 */
@Component
public class PassiveState implements SystemState {

    private static final Logger log = LoggerFactory.getLogger(PassiveState.class);
    /**
     * Distancia a partir de la cual el obstáculo se considera despejado.
     * Coincide con el umbral de recuperación de la histéresis del firmware
     * (sketch.ino: 100 cm + 15 cm) y del simulador (alertas.ts: 115 cm).
     */
    public static final double CLEAR_THRESHOLD_CM = 115.0;

    @Override
    public void handle(SystemContext context, EventRecord event) {
        String mode = event.getMode() != null ? event.getMode().toUpperCase() : "";

        if ("ACTIVE".equals(mode)) {
            log.info("[State] Pasivo → Activo (botón presionado)");
            context.setState(context.getActiveState());
            return;
        }

        // Si la distancia ya es segura, volver a inactivo
        if (event.getDistanceCm() != null && event.getDistanceCm() >= CLEAR_THRESHOLD_CM) {
            log.info("[State] Pasivo → Inactivo (obstáculo despejado, distancia: {} cm)", event.getDistanceCm());
            context.setState(context.getInactiveState());
        } else {
            log.debug("[State] Pasivo → Pasivo (obstáculo persiste a {} cm)", event.getDistanceCm());
        }
    }

    @Override
    public String getName() {
        return "PASSIVE";
    }
}
