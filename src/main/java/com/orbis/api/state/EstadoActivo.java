package com.orbis.api.state;

import com.orbis.api.model.RegistroEvento;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Patrón State — Estado Activo.
 * El sistema está procesando la imagen con Google Vision.
 * Transición:
 *   - Siempre vuelve a EstadoInactivo después de procesar (el análisis es puntual).
 */
@Component
public class EstadoActivo implements EstadoSistema {

    private static final Logger log = LoggerFactory.getLogger(EstadoActivo.class);

    @Override
    public void manejarEvento(ContextoSistema contexto, RegistroEvento evento) {
        log.info("[State] Activo → Inactivo (análisis completado para evento modo={})", evento.getModo());
        // Después de analizar, el sistema siempre vuelve a reposo
        contexto.establecerEstado(contexto.getEstadoInactivo());
    }

    @Override
    public String getNombre() {
        return "ACTIVO";
    }
}
