package com.orbis.api;

import com.orbis.api.model.EventRecord;
import com.orbis.api.state.SystemContext;
import com.orbis.api.state.ActiveState;
import com.orbis.api.state.InactiveState;
import com.orbis.api.state.PassiveState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Patrón STATE: transiciones de SystemContext entre
 * InactiveState, PassiveState y ActiveState.
 */
class StatePatternTest {

    private SystemContext context;

    @BeforeEach
    void setUp() {
        context = new SystemContext(new InactiveState(), new PassiveState(), new ActiveState());
    }

    private static EventRecord event(String mode, Double distance) {
        EventRecord e = new EventRecord();
        e.setMode(mode);
        e.setDistanceCm(distance);
        return e;
    }

    // ── Estado inicial y respuestas ───────────────────────────────

    @Test
    @DisplayName("Estado inicial: INACTIVE")
    void initial_state_inactive() {
        assertThat(context.getCurrentStateName()).isEqualTo("INACTIVE");
    }

    @Test
    @DisplayName("Cada estado responde con su nombre")
    void state_names() {
        assertThat(new InactiveState().getName()).isEqualTo("INACTIVE");
        assertThat(new PassiveState().getName()).isEqualTo("PASSIVE");
        assertThat(new ActiveState().getName()).isEqualTo("ACTIVE");
    }

    // ── Desde INACTIVE ────────────────────────────────────────────

    @Test
    @DisplayName("INACTIVE + evento PASSIVE -> PASSIVE")
    void inactive_to_passive() {
        context.processEvent(event("PASSIVE", 40.0));
        assertThat(context.getCurrentStateName()).isEqualTo("PASSIVE");
    }

    @Test
    @DisplayName("INACTIVE + evento ACTIVE -> ACTIVE")
    void inactive_to_active() {
        context.processEvent(event("ACTIVE", 40.0));
        assertThat(context.getCurrentStateName()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("INACTIVE + modo desconocido o nulo -> permanece INACTIVE")
    void inactive_unknown_mode_stays() {
        context.processEvent(event("WEIRD", 40.0));
        assertThat(context.getCurrentStateName()).isEqualTo("INACTIVE");

        context.processEvent(event(null, 40.0));
        assertThat(context.getCurrentStateName()).isEqualTo("INACTIVE");
    }

    @Test
    @DisplayName("El modo se interpreta sin distinguir mayúsculas")
    void mode_case_insensitive() {
        context.processEvent(event("passive", 40.0));
        assertThat(context.getCurrentStateName()).isEqualTo("PASSIVE");
    }

    // ── Desde PASSIVE ──────────────────────────────────────────────

    @Test
    @DisplayName("PASSIVE + evento ACTIVE (botón) -> ACTIVE")
    void passive_to_active() {
        context.setState(context.getPassiveState());
        context.processEvent(event("ACTIVE", 40.0));
        assertThat(context.getCurrentStateName()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("PASSIVE + obstáculo persiste (< umbral de recuperación) -> permanece PASSIVE")
    void passive_persists_if_obstacle_near() {
        context.setState(context.getPassiveState());
        context.processEvent(event("PASSIVE", PassiveState.CLEAR_THRESHOLD_CM - 0.1));
        assertThat(context.getCurrentStateName()).isEqualTo("PASSIVE");
    }

    @Test
    @DisplayName("PASSIVE + distancia >= umbral de recuperación (115 cm) -> INACTIVE")
    void passive_to_inactive_when_clear() {
        context.setState(context.getPassiveState());
        context.processEvent(event("PASSIVE", PassiveState.CLEAR_THRESHOLD_CM));
        assertThat(context.getCurrentStateName()).isEqualTo("INACTIVE");
    }

    @Test
    @DisplayName("El umbral de recuperación del backend coincide con firmware/simulador (115 cm)")
    void threshold_aligned_with_hysteresis() {
        assertThat(PassiveState.CLEAR_THRESHOLD_CM).isEqualTo(115.0);
    }

    @Test
    @DisplayName("PASSIVE + distancia nula -> permanece PASSIVE")
    void passive_null_distance_stays() {
        context.setState(context.getPassiveState());
        context.processEvent(event("PASSIVE", null));
        assertThat(context.getCurrentStateName()).isEqualTo("PASSIVE");
    }

    // ── Desde ACTIVE ──────────────────────────────────────────────

    @Test
    @DisplayName("ACTIVE + cualquier evento -> INACTIVE (análisis completado)")
    void active_returns_to_inactive() {
        context.setState(context.getActiveState());
        context.processEvent(event("ACTIVE", 40.0));
        assertThat(context.getCurrentStateName()).isEqualTo("INACTIVE");

        context.setState(context.getActiveState());
        context.processEvent(event("PASSIVE", 10.0));
        assertThat(context.getCurrentStateName()).isEqualTo("INACTIVE");
    }

    // ── Ciclo completo ────────────────────────────────────────────

    @Test
    @DisplayName("Ciclo: INACTIVE -> PASSIVE -> ACTIVE -> INACTIVE -> PASSIVE -> INACTIVE")
    void full_cycle() {
        context.processEvent(event("PASSIVE", 30.0));
        assertThat(context.getCurrentStateName()).isEqualTo("PASSIVE");

        context.processEvent(event("ACTIVE", 30.0));
        assertThat(context.getCurrentStateName()).isEqualTo("ACTIVE");

        context.processEvent(event("ACTIVE", 30.0));
        assertThat(context.getCurrentStateName()).isEqualTo("INACTIVE");

        context.processEvent(event("PASSIVE", 30.0));
        assertThat(context.getCurrentStateName()).isEqualTo("PASSIVE");

        context.processEvent(event("PASSIVE", 200.0));
        assertThat(context.getCurrentStateName()).isEqualTo("INACTIVE");
    }

    @Test
    @DisplayName("El contexto expone los tres estados concretos")
    void context_exposes_states() {
        assertThat(context.getInactiveState()).isInstanceOf(InactiveState.class);
        assertThat(context.getPassiveState()).isInstanceOf(PassiveState.class);
        assertThat(context.getActiveState()).isInstanceOf(ActiveState.class);
    }
}
