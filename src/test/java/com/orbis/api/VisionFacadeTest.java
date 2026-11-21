package com.orbis.api;

import com.orbis.api.command.QueryAiCommand;
import com.orbis.api.command.CommandExecutor;
import com.orbis.api.facade.VisionAdapter;
import com.orbis.api.facade.VisionException;
import com.orbis.api.facade.VisionFacade;
import com.orbis.api.model.EventRecord;
import com.orbis.api.repository.EventRecordRepository;
import com.orbis.api.strategy.ActiveStrategy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Patrón FACADE / ADAPTER.
 * VisionFacade es la interfaz que ve el dominio; VisionAdapter es la implementación que
 * valida, normaliza y filtra el formato crudo del cliente. (Los casos finos de formato
 * están en VisionAdapterTest; aquí se comprueba la COORDINACIÓN y la propagación de errores.)
 */
@ExtendWith(MockitoExtension.class)
class VisionFacadeTest {

    @Mock VisionFacade facadeMock;
    @Mock EventRecordRepository repository;
    @Mock ApplicationEventPublisher publisher;

    @Test
    @DisplayName("VisionAdapter implementa VisionFacade")
    void adapter_implements_facade() {
        assertThat(new VisionAdapter()).isInstanceOf(VisionFacade.class);
    }

    @Test
    @DisplayName("A través de la interfaz: valida, normaliza y filtra por umbral")
    void facade_normalizes_via_interface() {
        VisionFacade facade = new VisionAdapter();

        List<String> outcome = facade.analyzeImage("person (92%), noise (10%), chair");

        assertThat(outcome).containsExactly("Person (92%)", "Chair");
    }

    @Test
    @DisplayName("Entrada corrupta -> VisionException a través de la interfaz")
    void facade_propagates_vision_exception() {
        VisionFacade facade = new VisionAdapter();

        assertThatThrownBy(() -> facade.analyzeImage(null)).isInstanceOf(VisionException.class);
        assertThatThrownBy(() -> facade.analyzeImage("ERROR_IA: fallo")).isInstanceOf(VisionException.class);
        assertThatThrownBy(() -> facade.analyzeImage("data:image/png;base64,AAAA")).isInstanceOf(VisionException.class);
        assertThatThrownBy(() -> facade.analyzeImage("<img src=x onerror=alert(1)>")).isInstanceOf(VisionException.class);
    }

    @Test
    @DisplayName("Todas las etiquetas bajo el umbral -> lista vacía (sin excepción)")
    void facade_all_below_threshold_returns_empty() {
        assertThat(new VisionAdapter().analyzeImage("Noise (10%), Blur (20%)")).isEmpty();
    }

    @Test
    @DisplayName("ActiveStrategy + VisionAdapter real: etiqueta corrupta -> ERROR_VISION y se guarda")
    void active_strategy_handles_real_vision_exception() {
        lenient().when(repository.save(any(EventRecord.class))).thenAnswer(inv -> inv.getArgument(0));
        ActiveStrategy strategy = new ActiveStrategy(repository, new VisionAdapter(), new CommandExecutor(), publisher);
        EventRecord e = new EventRecord();
        e.setMode("ACTIVE");
        e.setDistanceCm(35.0);
        e.setAiLabels("<script>alert(1)</script>");

        EventRecord outcome = strategy.process(e);

        assertThat(outcome.getAiLabels()).startsWith("ERROR_VISION:");
        verify(repository).save(e);
    }

    @Test
    @DisplayName("QueryAiCommand coordina con la fachada y propaga VisionException")
    void query_ai_command_propagates_error() {
        when(facadeMock.analyzeImage("x")).thenThrow(new VisionException("fallo de visión"));
        QueryAiCommand command = new QueryAiCommand(facadeMock, "x");

        assertThatThrownBy(command::execute)
                .isInstanceOf(VisionException.class)
                .hasMessage("fallo de visión");
    }

    @Test
    @DisplayName("VisionException conserva la causa original")
    void vision_exception_con_causa() {
        Throwable cause = new IllegalStateException("origen");
        VisionException ex = new VisionException("envoltorio", cause);

        assertThat(ex).hasMessage("envoltorio").hasCause(cause);
    }
}
