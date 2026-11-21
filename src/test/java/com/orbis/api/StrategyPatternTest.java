package com.orbis.api;

import com.orbis.api.command.CommandExecutor;
import com.orbis.api.facade.VisionAdapter;
import com.orbis.api.facade.VisionException;
import com.orbis.api.facade.VisionFacade;
import com.orbis.api.model.EventRecord;
import com.orbis.api.observer.EventRecordedEvent;
import com.orbis.api.repository.EventRecordRepository;
import com.orbis.api.strategy.ActiveStrategy;
import com.orbis.api.strategy.PassiveStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Patrón STRATEGY.
 *  - PassiveStrategy: guarda telemetría sin tocar IA.
 *  - ActiveStrategy: usa la fachada/adaptador de visión y guarda etiquetas normalizadas.
 */
@ExtendWith(MockitoExtension.class)
class StrategyPatternTest {

    @Mock EventRecordRepository repository;
    @Mock ApplicationEventPublisher publisher;
    @Mock VisionFacade visionFacade;

    private final CommandExecutor executor = new CommandExecutor();

    @BeforeEach
    void setUp() {
        // El repositorio devuelve la misma entidad que recibe
        lenient().when(repository.save(any(EventRecord.class)))
                 .thenAnswer(inv -> inv.getArgument(0));
    }

    private static EventRecord event(String mode, double distance, String labels) {
        EventRecord e = new EventRecord();
        e.setMode(mode);
        e.setDistanceCm(distance);
        e.setAiLabels(labels);
        return e;
    }

    // ── PASSIVE ────────────────────────────────────────────────────

    @Test
    @DisplayName("PassiveStrategy guarda el evento y no usa IA (no depende de VisionFacade)")
    void passive_saves_without_ai() {
        PassiveStrategy strategy = new PassiveStrategy(repository, executor, publisher);
        EventRecord input = event("PASSIVE", 42.0, null);

        EventRecord outcome = strategy.process(input);

        verify(repository).save(input);
        assertThat(outcome.getAiLabels()).isNull();
        verifyNoInteractions(visionFacade);
        // Garantía estructural: la estrategia ni siquiera recibe una fachada de visión
        assertThat(PassiveStrategy.class.getConstructors()[0].getParameterTypes())
                .doesNotContain(VisionFacade.class);
    }

    @Test
    @DisplayName("PassiveStrategy publica EventRecordedEvent tras guardar")
    void passive_publishes_event() {
        PassiveStrategy strategy = new PassiveStrategy(repository, executor, publisher);

        strategy.process(event("PASSIVE", 30.0, null));

        verify(publisher).publishEvent(any(EventRecordedEvent.class));
    }

    // ── ACTIVE ────────────────────────────────────────────────────

    @Test
    @DisplayName("ActiveStrategy normaliza y filtra etiquetas del cliente con el VisionAdapter real")
    void active_normalizes_labels_with_real_adapter() {
        ActiveStrategy strategy = new ActiveStrategy(repository, new VisionAdapter(), executor, publisher);
        EventRecord input = event("ACTIVE", 60.0, "chair (85%), noise (30%), person (92%)");

        EventRecord outcome = strategy.process(input);

        // "noise (30%)" cae bajo el umbral de confianza (40%) y se capitaliza el resto
        assertThat(outcome.getAiLabels()).isEqualTo("Chair (85%), Person (92%)");
        verify(publisher).publishEvent(any(EventRecordedEvent.class));
    }

    @Test
    @DisplayName("ActiveStrategy pasa las etiquetas por la fachada y guarda lo que devuelve")
    void active_uses_facade_for_labels() {
        when(visionFacade.analyzeImage("laptop, mesa")).thenReturn(List.of("Laptop", "Mesa"));
        ActiveStrategy strategy = new ActiveStrategy(repository, visionFacade, executor, publisher);

        EventRecord outcome = strategy.process(event("ACTIVE", 50.0, "laptop, mesa"));

        verify(visionFacade).analyzeImage("laptop, mesa");
        assertThat(outcome.getAiLabels()).isEqualTo("Laptop, Mesa");
    }

    @Test
    @DisplayName("ActiveStrategy: VisionException -> ERROR_VISION y el evento se guarda igualmente")
    void active_vision_exception_does_not_reject_event() {
        when(visionFacade.analyzeImage(anyString())).thenThrow(new VisionException("boom"));
        ActiveStrategy strategy = new ActiveStrategy(repository, visionFacade, executor, publisher);

        EventRecord outcome = strategy.process(event("ACTIVE", 50.0, "algo"));

        assertThat(outcome.getAiLabels()).isEqualTo("ERROR_VISION: boom");
        ArgumentCaptor<EventRecord> captor = ArgumentCaptor.forClass(EventRecord.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getAiLabels()).startsWith("ERROR_VISION");
    }

    @Test
    @DisplayName("ActiveStrategy: flujo imagen Base64 consulta la fachada mediante QueryAiCommand")
    void active_base64_image_flow() {
        EventRecord input = event("ACTIVE", 40.0, null);
        input.setImageUrl("BASE64DATA");
        when(visionFacade.analyzeImage("BASE64DATA")).thenReturn(List.of("Chair"));
        ActiveStrategy strategy = new ActiveStrategy(repository, visionFacade, executor, publisher);

        EventRecord outcome = strategy.process(input);

        verify(visionFacade).analyzeImage("BASE64DATA");
        assertThat(outcome.getAiLabels()).isEqualTo("Chair");
    }

    @Test
    @DisplayName("ActiveStrategy: sin imagen ni etiquetas -> SIN_IMAGEN y sin llamar a la fachada")
    void active_without_image_or_labels() {
        ActiveStrategy strategy = new ActiveStrategy(repository, visionFacade, executor, publisher);

        EventRecord outcome = strategy.process(event("ACTIVE", 40.0, null));

        assertThat(outcome.getAiLabels()).isEqualTo("NO_IMAGE");
        verify(visionFacade, never()).analyzeImage(anyString());
    }
}
