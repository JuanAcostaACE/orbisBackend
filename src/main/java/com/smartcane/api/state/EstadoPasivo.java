package com.smartcane.api.state;

import com.smartcane.api.model.RegistroEvento;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Patrón State — Estado Pasivo.
 * El sistema detectó un obstáculo y está vibrando.
 * Transiciones:
 *   - Recibe ACTIVO → pasa a EstadoActivo (usuario presionó el botón)
 *   - Recibe PASIVO → permanece (sigue detectando obstáculo)
 *   - Distancia > umbral → vuelve a EstadoInactivo
 */
@Component
public class EstadoPasivo implements EstadoSistema {

    private static final Logger log = LoggerFactory.getLogger(EstadoPasivo.class);
    private static final double UMBRAL_LIBRE_CM = 80.0;

    @Override
    public void manejarEvento(ContextoSistema contexto, RegistroEvento evento) {
        String modo = evento.getModo() != null ? evento.getModo().toUpperCase() : "";

        if ("ACTIVO".equals(modo)) {
            log.info("[State] Pasivo → Activo (botón presionado)");
            contexto.establecerEstado(contexto.getEstadoActivo());
            return;
        }

        // Si la distancia ya es segura, volver a inactivo
        if (evento.getDistanciaCm() != null && evento.getDistanciaCm() >= UMBRAL_LIBRE_CM) {
            log.info("[State] Pasivo → Inactivo (obstáculo despejado, distancia: {} cm)", evento.getDistanciaCm());
            contexto.establecerEstado(contexto.getEstadoInactivo());
        } else {
            log.debug("[State] Pasivo → Pasivo (obstáculo persiste a {} cm)", evento.getDistanciaCm());
        }
    }

    @Override
    public String getNombre() {
        return "PASIVO";
    }
}
