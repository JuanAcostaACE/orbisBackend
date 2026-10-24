package com.orbis.api;

import com.orbis.api.command.EjecutorComando;
import com.orbis.api.facade.VisionFacade;
import com.orbis.api.factory.CreadorActivoStrategy;
import com.orbis.api.factory.CreadorPasivoStrategy;
import com.orbis.api.factory.CreadorStrategy;
import com.orbis.api.factory.StrategyFactory;
import com.orbis.api.model.RegistroEvento;
import com.orbis.api.repository.RegistroEventoRepository;
import com.orbis.api.strategy.ActivoStrategy;
import com.orbis.api.strategy.ModoProcesamientoStrategy;
import com.orbis.api.strategy.PasivoStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

/**
 * Patrón FACTORY METHOD.
 *  - CreadorPasivoStrategy / CreadorActivoStrategy: creators concretos.
 *  - StrategyFactory: elige el creator por modo y rechaza modos inválidos.
 */
@ExtendWith(MockitoExtension.class)
class FactoryMethodPatternTest {

    @Mock RegistroEventoRepository repository;
    @Mock ApplicationEventPublisher publisher;
    @Mock VisionFacade visionFacade;

    private final EjecutorComando ejecutor = new EjecutorComando();

    private CreadorPasivoStrategy creadorPasivo;
    private CreadorActivoStrategy creadorActivo;
    private StrategyFactory factory;

    @BeforeEach
    void setUp() {
        creadorPasivo = new CreadorPasivoStrategy(repository, ejecutor, publisher);
        creadorActivo = new CreadorActivoStrategy(repository, visionFacade, ejecutor, publisher);
        factory       = new StrategyFactory(creadorPasivo, creadorActivo);
        lenient().when(repository.save(any(RegistroEvento.class)))
                 .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("Los creators concretos extienden el creator abstracto (Factory Method)")
    void creators_extienden_creator_abstracto() {
        assertThat(creadorPasivo).isInstanceOf(CreadorStrategy.class);
        assertThat(creadorActivo).isInstanceOf(CreadorStrategy.class);
    }

    @Test
    @DisplayName("CreadorPasivoStrategy crea PasivoStrategy")
    void creador_pasivo_crea_pasivo() {
        assertThat(creadorPasivo.crearEstrategia()).isInstanceOf(PasivoStrategy.class);
    }

    @Test
    @DisplayName("CreadorActivoStrategy crea ActivoStrategy")
    void creador_activo_crea_activo() {
        assertThat(creadorActivo.crearEstrategia()).isInstanceOf(ActivoStrategy.class);
    }

    @Test
    @DisplayName("Cada llamada del creator produce una instancia nueva")
    void creator_produce_instancias_nuevas() {
        ModoProcesamientoStrategy a = creadorPasivo.crearEstrategia();
        ModoProcesamientoStrategy b = creadorPasivo.crearEstrategia();
        assertThat(a).isNotSameAs(b);
    }

    @Test
    @DisplayName("StrategyFactory resuelve PASIVO -> PasivoStrategy")
    void factory_resuelve_pasivo() {
        assertThat(factory.obtenerEstrategia("PASIVO")).isInstanceOf(PasivoStrategy.class);
    }

    @Test
    @DisplayName("StrategyFactory resuelve ACTIVO -> ActivoStrategy")
    void factory_resuelve_activo() {
        assertThat(factory.obtenerEstrategia("ACTIVO")).isInstanceOf(ActivoStrategy.class);
    }

    @Test
    @DisplayName("StrategyFactory ignora mayúsculas/minúsculas")
    void factory_ignora_mayusculas() {
        assertThat(factory.obtenerEstrategia("pasivo")).isInstanceOf(PasivoStrategy.class);
        assertThat(factory.obtenerEstrategia("Activo")).isInstanceOf(ActivoStrategy.class);
    }

    @Test
    @DisplayName("StrategyFactory rechaza modo nulo con IllegalArgumentException")
    void factory_rechaza_nulo() {
        assertThatThrownBy(() -> factory.obtenerEstrategia(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nulo");
    }

    @ParameterizedTest(name = "modo inválido \"{0}\" -> IllegalArgumentException")
    @ValueSource(strings = {"OTRO", "INVALIDO", "", " ", "PASIVO "})
    void factory_rechaza_modos_invalidos(String modo) {
        assertThatThrownBy(() -> factory.obtenerEstrategia(modo))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no soportado");
    }

    @Test
    @DisplayName("Template method: procesarConEstrategiaCreada delega en la estrategia creada")
    void creator_procesa_con_estrategia_creada() {
        RegistroEvento e = new RegistroEvento();
        e.setModo("PASIVO");
        e.setDistanciaCm(20.0);

        RegistroEvento resultado = creadorPasivo.procesarConEstrategiaCreada(e);

        verify(repository).save(e);
        assertThat(resultado).isSameAs(e);
    }
}
