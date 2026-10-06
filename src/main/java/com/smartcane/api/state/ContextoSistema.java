package com.smartcane.api.state;

import com.smartcane.api.model.RegistroEvento;
import org.springframework.stereotype.Component;

/**
 * Patrón State — Contexto.
 * Mantiene una referencia al estado actual del sistema SmartCane.
 * Es el único punto que conoce todos los estados posibles;
 * el exterior solo interactúa con procesarEvento() sin saber en qué estado está.
 *
 * Integración:
 *   EventoService llama a procesarEvento() ANTES de que la Strategy procese,
 *   de modo que el estado ya transiciona al correcto antes de la lógica de negocio.
 */
@Component
public class ContextoSistema {

    private EstadoSistema estadoActual;

    // Referencias a los estados concretos (inyectadas por Spring)
    private final EstadoInactivo estadoInactivo;
    private final EstadoPasivo   estadoPasivo;
    private final EstadoActivo   estadoActivo;

    public ContextoSistema(EstadoInactivo estadoInactivo,
                           EstadoPasivo   estadoPasivo,
                           EstadoActivo   estadoActivo) {
        this.estadoInactivo = estadoInactivo;
        this.estadoPasivo   = estadoPasivo;
        this.estadoActivo   = estadoActivo;
        this.estadoActual   = estadoInactivo; // Estado inicial del sistema
    }

    /** Delega al estado actual la decisión de cómo manejar el evento. */
    public void procesarEvento(RegistroEvento evento) {
        estadoActual.manejarEvento(this, evento);
    }

    /** Cambia el estado actual (llamado desde los estados concretos). */
    public void establecerEstado(EstadoSistema nuevoEstado) {
        this.estadoActual = nuevoEstado;
    }

    public String getNombreEstadoActual() {
        return estadoActual.getNombre();
    }

    // Getters para que los estados concretos puedan solicitar transiciones
    public EstadoInactivo getEstadoInactivo() { return estadoInactivo; }
    public EstadoPasivo   getEstadoPasivo()   { return estadoPasivo;   }
    public EstadoActivo   getEstadoActivo()   { return estadoActivo;   }
}
