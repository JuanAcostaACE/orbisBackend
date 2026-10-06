package com.smartcane.api.state;

import com.smartcane.api.model.RegistroEvento;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Patrón State — Estado Inactivo.
 * Estado inicial del sistema cuando no hay obstáculo detectado.
 * Transiciones:
 *   - Recibe PASIVO → pasa a EstadoPasivo (obstáculo detectado)
 *   - Recibe ACTIVO → pasa a EstadoActivo (análisis solicitado)
 */
@Component
public class EstadoInactivo implements EstadoSistema {

    private static final Logger log = LoggerFactory.getLogger(EstadoInactivo.class);

    @Override
    public void manejarEvento(ContextoSistema contexto, RegistroEvento evento) {
        String modo = evento.getModo() != null ? evento.getModo().toUpperCase() : "";
        switch (modo) {
            case "PASIVO" -> {
                log.info("[State] Inactivo → Pasivo (obstáculo a {} cm)", evento.getDistanciaCm());
                contexto.establecerEstado(contexto.getEstadoPasivo());
            }
            case "ACTIVO" -> {
                log.info("[State] Inactivo → Activo (análisis solicitado)");
                contexto.establecerEstado(contexto.getEstadoActivo());
            }
            default -> log.warn("[State] EstadoInactivo: modo desconocido '{}'", evento.getModo());
        }
    }

    @Override
    public String getNombre() {
        return "INACTIVO";
    }
}
