package com.orbis.api;

import com.orbis.api.command.CommandExecutor;
import com.orbis.api.facade.VisionFacade;
import com.orbis.api.factory.ActiveStrategyCreator;
import com.orbis.api.factory.PassiveStrategyCreator;
import com.orbis.api.factory.StrategyCreator;
import com.orbis.api.factory.StrategyFactory;
import com.orbis.api.model.EventRecord;
import com.orbis.api.repository.EventRecordRepository;
import com.orbis.api.strategy.ActiveStrategy;
import com.orbis.api.strategy.ProcessingModeStrategy;
import com.orbis.api.strategy.PassiveStrategy;
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
 *  - PassiveStrategyCreator / ActiveStrategyCreator: creators concretos.
 *  - StrategyFactory: elige el creator por modo y rechaza modos inválidos.
 */
@ExtendWith(MockitoExtension.class)
class FactoryMethodPatternTest {

    @Mock EventRecordRepository repository;
    @Mock ApplicationEventPublisher publisher;
    @Mock VisionFacade visionFacade;

    private final CommandExecutor executor = new CommandExecutor();

    private PassiveStrategyCreator passiveCreator;
    private ActiveStrategyCreator activeCreator;
    private StrategyFactory factory;

    @BeforeEach
    void setUp() {
        passiveCreator = new PassiveStrategyCreator(repository, executor, publisher);
        activeCreator = new ActiveStrategyCreator(repository, visionFacade, executor, publisher);
        factory       = new StrategyFactory(passiveCreator, activeCreator);
        lenient().when(repository.save(any(EventRecord.class)))
                 .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("Los creators concretos extienden el creator abstracto (Factory Method)")
    void creators_extend_abstract_creator() {
        assertThat(passiveCreator).isInstanceOf(StrategyCreator.class);
        assertThat(activeCreator).isInstanceOf(StrategyCreator.class);
    }

    @Test
    @DisplayName("PassiveStrategyCreator crea PassiveStrategy")
    void passive_creator_creates_passive() {
        assertThat(passiveCreator.createStrategy()).isInstanceOf(PassiveStrategy.class);
    }

    @Test
    @DisplayName("ActiveStrategyCreator crea ActiveStrategy")
    void active_creator_creates_active() {
        assertThat(activeCreator.createStrategy()).isInstanceOf(ActiveStrategy.class);
    }

    @Test
    @DisplayName("Cada llamada del creator produce una instancia nueva")
    void creator_produces_new_instances() {
        ProcessingModeStrategy a = passiveCreator.createStrategy();
        ProcessingModeStrategy b = passiveCreator.createStrategy();
        assertThat(a).isNotSameAs(b);
    }

    @Test
    @DisplayName("StrategyFactory resuelve PASSIVE -> PassiveStrategy")
    void factory_resolves_passive() {
        assertThat(factory.getStrategy("PASSIVE")).isInstanceOf(PassiveStrategy.class);
    }

    @Test
    @DisplayName("StrategyFactory resuelve ACTIVE -> ActiveStrategy")
    void factory_resolves_active() {
        assertThat(factory.getStrategy("ACTIVE")).isInstanceOf(ActiveStrategy.class);
    }

    @Test
    @DisplayName("StrategyFactory ignora mayúsculas/minúsculas")
    void factory_ignores_case() {
        assertThat(factory.getStrategy("passive")).isInstanceOf(PassiveStrategy.class);
        assertThat(factory.getStrategy("Active")).isInstanceOf(ActiveStrategy.class);
    }

    @Test
    @DisplayName("StrategyFactory rechaza modo nulo con IllegalArgumentException")
    void factory_rejects_null() {
        assertThatThrownBy(() -> factory.getStrategy(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nulo");
    }

    @ParameterizedTest(name = "modo inválido \"{0}\" -> IllegalArgumentException")
    @ValueSource(strings = {"OTHER", "INVALID", "", " ", "PASSIVE "})
    void factory_rejects_invalid_modes(String mode) {
        assertThatThrownBy(() -> factory.getStrategy(mode))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no soportado");
    }

    @Test
    @DisplayName("Template method: processWithCreatedStrategy delega en la estrategia creada")
    void creator_processes_with_created_strategy() {
        EventRecord e = new EventRecord();
        e.setMode("PASSIVE");
        e.setDistanceCm(20.0);

        EventRecord outcome = passiveCreator.processWithCreatedStrategy(e);

        verify(repository).save(e);
        assertThat(outcome).isSameAs(e);
    }
}
