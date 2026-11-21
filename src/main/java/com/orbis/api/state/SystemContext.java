package com.orbis.api.state;

import com.orbis.api.model.EventRecord;
import org.springframework.stereotype.Component;

/**
 * Patrón State — Contexto.
 * Mantiene una referencia al estado actual del sistema SmartCane.
 * Es el único punto que conoce todos los estados posibles;
 * el exterior solo interactúa con processEvent() sin saber en qué estado está.
 *
 * Integración:
 *   EventService llama a processEvent() ANTES de que la Strategy procese,
 *   de modo que el estado ya transiciona al correcto antes de la lógica de negocio.
 */
@Component
public class SystemContext {

    private SystemState currentState;

    // Referencias a los estados concretos (inyectadas por Spring)
    private final InactiveState inactiveState;
    private final PassiveState   passiveState;
    private final ActiveState   activeState;

    public SystemContext(InactiveState inactiveState,
                           PassiveState   passiveState,
                           ActiveState   activeState) {
        this.inactiveState = inactiveState;
        this.passiveState   = passiveState;
        this.activeState   = activeState;
        this.currentState   = inactiveState; // Estado inicial del sistema
    }

    /** Delega al estado actual la decisión de cómo manejar el evento. */
    public void processEvent(EventRecord event) {
        currentState.handle(this, event);
    }

    /** Cambia el estado actual (llamado desde los estados concretos). */
    public void setState(SystemState newState) {
        this.currentState = newState;
    }

    public String getCurrentStateName() {
        return currentState.getName();
    }

    // Getters para que los estados concretos puedan solicitar transiciones
    public InactiveState getInactiveState() { return inactiveState; }
    public PassiveState   getPassiveState()   { return passiveState;   }
    public ActiveState   getActiveState()   { return activeState;   }
}
