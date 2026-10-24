package com.orbis.api;

import com.orbis.api.model.RegistroEvento;
import com.orbis.api.state.ContextoSistema;
import com.orbis.api.state.EstadoActivo;
import com.orbis.api.state.EstadoInactivo;
import com.orbis.api.state.EstadoPasivo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Patrón STATE: transiciones de ContextoSistema entre
 * EstadoInactivo, EstadoPasivo y EstadoActivo.
 */
class StatePatternTest {

    private ContextoSistema contexto;

    @BeforeEach
    void setUp() {
        contexto = new ContextoSistema(new EstadoInactivo(), new EstadoPasivo(), new EstadoActivo());
    }

    private static RegistroEvento evento(String modo, Double distancia) {
        RegistroEvento e = new RegistroEvento();
        e.setModo(modo);
        e.setDistanciaCm(distancia);
        return e;
    }

    // ── Estado inicial y respuestas ───────────────────────────────

    @Test
    @DisplayName("Estado inicial: INACTIVO")
    void estado_inicial_inactivo() {
        assertThat(contexto.getNombreEstadoActual()).isEqualTo("INACTIVO");
    }

    @Test
    @DisplayName("Cada estado responde con su nombre")
    void nombres_de_estado() {
        assertThat(new EstadoInactivo().getNombre()).isEqualTo("INACTIVO");
        assertThat(new EstadoPasivo().getNombre()).isEqualTo("PASIVO");
        assertThat(new EstadoActivo().getNombre()).isEqualTo("ACTIVO");
    }

    // ── Desde INACTIVO ────────────────────────────────────────────

    @Test
    @DisplayName("INACTIVO + evento PASIVO -> PASIVO")
    void inactivo_a_pasivo() {
        contexto.procesarEvento(evento("PASIVO", 40.0));
        assertThat(contexto.getNombreEstadoActual()).isEqualTo("PASIVO");
    }

    @Test
    @DisplayName("INACTIVO + evento ACTIVO -> ACTIVO")
    void inactivo_a_activo() {
        contexto.procesarEvento(evento("ACTIVO", 40.0));
        assertThat(contexto.getNombreEstadoActual()).isEqualTo("ACTIVO");
    }

    @Test
    @DisplayName("INACTIVO + modo desconocido o nulo -> permanece INACTIVO")
    void inactivo_modo_desconocido_permanece() {
        contexto.procesarEvento(evento("RARO", 40.0));
        assertThat(contexto.getNombreEstadoActual()).isEqualTo("INACTIVO");

        contexto.procesarEvento(evento(null, 40.0));
        assertThat(contexto.getNombreEstadoActual()).isEqualTo("INACTIVO");
    }

    @Test
    @DisplayName("El modo se interpreta sin distinguir mayúsculas")
    void modo_sin_distinguir_mayusculas() {
        contexto.procesarEvento(evento("pasivo", 40.0));
        assertThat(contexto.getNombreEstadoActual()).isEqualTo("PASIVO");
    }

    // ── Desde PASIVO ──────────────────────────────────────────────

    @Test
    @DisplayName("PASIVO + evento ACTIVO (botón) -> ACTIVO")
    void pasivo_a_activo() {
        contexto.establecerEstado(contexto.getEstadoPasivo());
        contexto.procesarEvento(evento("ACTIVO", 40.0));
        assertThat(contexto.getNombreEstadoActual()).isEqualTo("ACTIVO");
    }

    @Test
    @DisplayName("PASIVO + obstáculo persiste (< umbral de recuperación) -> permanece PASIVO")
    void pasivo_persiste_si_obstaculo_cerca() {
        contexto.establecerEstado(contexto.getEstadoPasivo());
        contexto.procesarEvento(evento("PASIVO", EstadoPasivo.UMBRAL_LIBRE_CM - 0.1));
        assertThat(contexto.getNombreEstadoActual()).isEqualTo("PASIVO");
    }

    @Test
    @DisplayName("PASIVO + distancia >= umbral de recuperación (115 cm) -> INACTIVO")
    void pasivo_a_inactivo_cuando_despeja() {
        contexto.establecerEstado(contexto.getEstadoPasivo());
        contexto.procesarEvento(evento("PASIVO", EstadoPasivo.UMBRAL_LIBRE_CM));
        assertThat(contexto.getNombreEstadoActual()).isEqualTo("INACTIVO");
    }

    @Test
    @DisplayName("El umbral de recuperación del backend coincide con firmware/simulador (115 cm)")
    void umbral_alineado_con_histeresis() {
        assertThat(EstadoPasivo.UMBRAL_LIBRE_CM).isEqualTo(115.0);
    }

    @Test
    @DisplayName("PASIVO + distancia nula -> permanece PASIVO")
    void pasivo_distancia_nula_permanece() {
        contexto.establecerEstado(contexto.getEstadoPasivo());
        contexto.procesarEvento(evento("PASIVO", null));
        assertThat(contexto.getNombreEstadoActual()).isEqualTo("PASIVO");
    }

    // ── Desde ACTIVO ──────────────────────────────────────────────

    @Test
    @DisplayName("ACTIVO + cualquier evento -> INACTIVO (análisis completado)")
    void activo_vuelve_a_inactivo() {
        contexto.establecerEstado(contexto.getEstadoActivo());
        contexto.procesarEvento(evento("ACTIVO", 40.0));
        assertThat(contexto.getNombreEstadoActual()).isEqualTo("INACTIVO");

        contexto.establecerEstado(contexto.getEstadoActivo());
        contexto.procesarEvento(evento("PASIVO", 10.0));
        assertThat(contexto.getNombreEstadoActual()).isEqualTo("INACTIVO");
    }

    // ── Ciclo completo ────────────────────────────────────────────

    @Test
    @DisplayName("Ciclo: INACTIVO -> PASIVO -> ACTIVO -> INACTIVO -> PASIVO -> INACTIVO")
    void ciclo_completo() {
        contexto.procesarEvento(evento("PASIVO", 30.0));
        assertThat(contexto.getNombreEstadoActual()).isEqualTo("PASIVO");

        contexto.procesarEvento(evento("ACTIVO", 30.0));
        assertThat(contexto.getNombreEstadoActual()).isEqualTo("ACTIVO");

        contexto.procesarEvento(evento("ACTIVO", 30.0));
        assertThat(contexto.getNombreEstadoActual()).isEqualTo("INACTIVO");

        contexto.procesarEvento(evento("PASIVO", 30.0));
        assertThat(contexto.getNombreEstadoActual()).isEqualTo("PASIVO");

        contexto.procesarEvento(evento("PASIVO", 200.0));
        assertThat(contexto.getNombreEstadoActual()).isEqualTo("INACTIVO");
    }

    @Test
    @DisplayName("El contexto expone los tres estados concretos")
    void contexto_expone_estados() {
        assertThat(contexto.getEstadoInactivo()).isInstanceOf(EstadoInactivo.class);
        assertThat(contexto.getEstadoPasivo()).isInstanceOf(EstadoPasivo.class);
        assertThat(contexto.getEstadoActivo()).isInstanceOf(EstadoActivo.class);
    }
}
