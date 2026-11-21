package com.orbis.api;

import com.orbis.api.command.CommandExecutor;
import com.orbis.api.facade.VisionAdapter;
import com.orbis.api.model.EventRecord;
import com.orbis.api.observer.EventRecordedEvent;
import com.orbis.api.observer.LogEventObserver;
import com.orbis.api.repository.EventRecordRepository;
import com.orbis.api.strategy.ActiveStrategy;
import com.orbis.api.strategy.PassiveStrategy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.support.GenericApplicationContext;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/**
 * Patrón OBSERVER (ApplicationEvent de Spring).
 * Se usa un ApplicationContext real y ligero como publicador, de modo que se verifica el
 * mecanismo de publicación/suscripción de verdad (no solo llamadas a un mock).
 */
@ExtendWith(MockitoExtension.class)
class ObserverPatternTest {

    @Mock EventRecordRepository repository;

    private GenericApplicationContext context;
    private final List<EventRecordedEvent> receivedA = new ArrayList<>();
    private final List<EventRecordedEvent> receivedB = new ArrayList<>();
    private final CommandExecutor executor = new CommandExecutor();

    @BeforeEach
    void setUp() {
        context = new GenericApplicationContext();
        context.addApplicationListener((ApplicationListener<ApplicationEvent>) ev -> {
            if (ev instanceof EventRecordedEvent e) receivedA.add(e);
        });
        context.addApplicationListener((ApplicationListener<ApplicationEvent>) ev -> {
            if (ev instanceof EventRecordedEvent e) receivedB.add(e);
        });
        context.refresh();

        // save() asigna un id, como haría la base de datos
        lenient().when(repository.save(any(EventRecord.class))).thenAnswer(inv -> {
            EventRecord e = inv.getArgument(0);
            e.setId(99L);
            return e;
        });
    }

    @AfterEach
    void tearDown() {
        context.close();
    }

    private static EventRecord event(String mode) {
        EventRecord e = new EventRecord();
        e.setMode(mode);
        e.setDistanceCm(25.0);
        return e;
    }

    @Test
    @DisplayName("Guardar un evento PASSIVE emite EventRecordedEvent con la entidad guardada")
    void passive_emits_recorded_event() {
        PassiveStrategy strategy = new PassiveStrategy(repository, executor, context);

        strategy.process(event("PASSIVE"));

        assertThat(receivedA).hasSize(1);
        assertThat(receivedA.get(0).getEvent().getId()).isEqualTo(99L);
        assertThat(receivedA.get(0).getEvent().getMode()).isEqualTo("PASSIVE");
    }

    @Test
    @DisplayName("Guardar un evento ACTIVE emite EventRecordedEvent")
    void active_emits_recorded_event() {
        ActiveStrategy strategy = new ActiveStrategy(repository, new VisionAdapter(), executor, context);
        EventRecord e = event("ACTIVE");
        e.setAiLabels("chair (80%)");

        strategy.process(e);

        assertThat(receivedA).hasSize(1);
        assertThat(receivedA.get(0).getEvent().getAiLabels()).isEqualTo("Chair (80%)");
    }

    @Test
    @DisplayName("Todos los observers suscritos reciben el mismo evento")
    void multiple_observers_receive_event() {
        new PassiveStrategy(repository, executor, context).process(event("PASSIVE"));

        assertThat(receivedA).hasSize(1);
        assertThat(receivedB).hasSize(1);
        assertThat(receivedA.get(0)).isSameAs(receivedB.get(0));
    }

    @Test
    @DisplayName("LogEventObserver recibe el evento publicado y delega en onEventRecorded")
    void log_observer_receives_event() {
        LogEventObserver observer = spy(new LogEventObserver());
        context.close();
        context = new GenericApplicationContext();
        context.addApplicationListener(observer);
        context.refresh();

        new PassiveStrategy(repository, executor, context).process(event("PASSIVE"));

        verify(observer).onEventRecorded(any(EventRecordedEvent.class));
    }

    @Test
    @DisplayName("PASSIVE: si falla la persistencia NO se emite el evento")
    void passive_does_not_emit_if_persistence_fails() {
        lenient().when(repository.save(any(EventRecord.class))).thenThrow(new RuntimeException("db caída"));
        PassiveStrategy strategy = new PassiveStrategy(repository, executor, context);

        assertThatThrownBy(() -> strategy.process(event("PASSIVE")))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("db caída");

        assertThat(receivedA).isEmpty();
        assertThat(receivedB).isEmpty();
    }

    @Test
    @DisplayName("ACTIVE: si falla la persistencia NO se emite el evento")
    void active_does_not_emit_if_persistence_fails() {
        lenient().when(repository.save(any(EventRecord.class))).thenThrow(new RuntimeException("db caída"));
        ActiveStrategy strategy = new ActiveStrategy(repository, new VisionAdapter(), executor, context);

        assertThatThrownBy(() -> strategy.process(event("ACTIVE")))
                .isInstanceOf(RuntimeException.class);

        assertThat(receivedA).isEmpty();
    }

    @Test
    @DisplayName("EventRecordedEvent expone la entidad y su origen")
    void event_exposes_entity_and_source() {
        EventRecord e = event("PASSIVE");
        Object origin = new Object();

        EventRecordedEvent ev = new EventRecordedEvent(origin, e);

        assertThat(ev.getEvent()).isSameAs(e);
        assertThat(ev.getSource()).isSameAs(origin);
    }
}
