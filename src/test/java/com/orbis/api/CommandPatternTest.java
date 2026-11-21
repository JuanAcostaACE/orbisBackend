package com.orbis.api;

import com.orbis.api.command.Command;
import com.orbis.api.command.QueryAiCommand;
import com.orbis.api.command.SaveEventCommand;
import com.orbis.api.command.CommandExecutor;
import com.orbis.api.facade.VisionException;
import com.orbis.api.facade.VisionFacade;
import com.orbis.api.model.EventRecord;
import com.orbis.api.repository.EventRecordRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Patrón COMMAND: CommandExecutor (invoker), SaveEventCommand y QueryAiCommand
 * (comandos concretos) con sus dependencias aisladas por mocks.
 */
@ExtendWith(MockitoExtension.class)
class CommandPatternTest {

    @Mock EventRecordRepository repository;
    @Mock VisionFacade visionFacade;

    private final CommandExecutor executor = new CommandExecutor();

    // ── CommandExecutor ───────────────────────────────────────────

    @Test
    @DisplayName("CommandExecutor ejecuta el comando una vez y devuelve su resultado")
    void executor_executes_and_returns_result() {
        AtomicInteger calls = new AtomicInteger();
        Command<String> command = () -> { calls.incrementAndGet(); return "ok"; };

        String outcome = executor.execute(command);

        assertThat(outcome).isEqualTo("ok");
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("CommandExecutor propaga las excepciones del comando")
    void executor_propagates_exceptions() {
        Command<String> command = () -> { throw new IllegalStateException("falló"); };

        assertThatThrownBy(() -> executor.execute(command))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("falló");
    }

    // ── SaveEventCommand ──────────────────────────────────────

    @Test
    @DisplayName("SaveEventCommand: no toca el repositorio hasta ejecutar() y luego guarda")
    void save_is_deferred_and_persists() {
        EventRecord input = new EventRecord();
        input.setMode("PASSIVE");
        EventRecord saved = new EventRecord();
        saved.setId(7L);
        when(repository.save(input)).thenReturn(saved);

        SaveEventCommand command = new SaveEventCommand(repository, input);
        verifyNoInteractions(repository);              // construir el comando no ejecuta nada

        EventRecord outcome = executor.execute(command);

        verify(repository).save(input);
        assertThat(outcome).isSameAs(saved);
        assertThat(outcome.getId()).isEqualTo(7L);
    }

    @Test
    @DisplayName("SaveEventCommand propaga el fallo de persistencia")
    void save_propagates_failure() {
        EventRecord input = new EventRecord();
        when(repository.save(input)).thenThrow(new RuntimeException("db caída"));

        assertThatThrownBy(() -> executor.execute(new SaveEventCommand(repository, input)))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("db caída");
    }

    // ── QueryAiCommand ────────────────────────────────────────

    @Test
    @DisplayName("QueryAiCommand delega en la fachada con el mismo argumento")
    void query_ai_delegates_to_facade() {
        when(visionFacade.analyzeImage("chair, table")).thenReturn(List.of("Chair", "Table"));

        List<String> outcome = executor.execute(new QueryAiCommand(visionFacade, "chair, table"));

        verify(visionFacade).analyzeImage("chair, table");
        assertThat(outcome).containsExactly("Chair", "Table");
    }

    @Test
    @DisplayName("QueryAiCommand no consulta la fachada hasta ejecutar()")
    void query_ai_is_deferred() {
        new QueryAiCommand(visionFacade, "x");
        verifyNoInteractions(visionFacade);
    }

    @Test
    @DisplayName("QueryAiCommand propaga VisionException a través del ejecutor")
    void query_ai_propagates_vision_exception() {
        when(visionFacade.analyzeImage("mal")).thenThrow(new VisionException("entrada inválida"));

        assertThatThrownBy(() -> executor.execute(new QueryAiCommand(visionFacade, "mal")))
                .isInstanceOf(VisionException.class)
                .hasMessage("entrada inválida");
    }
}
