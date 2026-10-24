package com.orbis.api;

import com.orbis.api.command.EjecutorComando;
import com.orbis.api.facade.VisionAdapter;
import com.orbis.api.model.RegistroEvento;
import com.orbis.api.observer.EventoRegistradoEvent;
import com.orbis.api.observer.LogEventoObserver;
import com.orbis.api.repository.RegistroEventoRepository;
import com.orbis.api.strategy.ActivoStrategy;
import com.orbis.api.strategy.PasivoStrategy;
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

    @Mock RegistroEventoRepository repository;

    private GenericApplicationContext contexto;
    private final List<EventoRegistradoEvent> recibidosA = new ArrayList<>();
    private final List<EventoRegistradoEvent> recibidosB = new ArrayList<>();
    private final EjecutorComando ejecutor = new EjecutorComando();

    @BeforeEach
    void setUp() {
        contexto = new GenericApplicationContext();
        contexto.addApplicationListener((ApplicationListener<ApplicationEvent>) ev -> {
            if (ev instanceof EventoRegistradoEvent e) recibidosA.add(e);
        });
        contexto.addApplicationListener((ApplicationListener<ApplicationEvent>) ev -> {
            if (ev instanceof EventoRegistradoEvent e) recibidosB.add(e);
        });
        contexto.refresh();

        // save() asigna un id, como haría la base de datos
        lenient().when(repository.save(any(RegistroEvento.class))).thenAnswer(inv -> {
            RegistroEvento e = inv.getArgument(0);
            e.setId(99L);
            return e;
        });
    }

    @AfterEach
    void tearDown() {
        contexto.close();
    }

    private static RegistroEvento evento(String modo) {
        RegistroEvento e = new RegistroEvento();
        e.setModo(modo);
        e.setDistanciaCm(25.0);
        return e;
    }

    @Test
    @DisplayName("Guardar un evento PASIVO emite EventoRegistradoEvent con la entidad guardada")
    void pasivo_emite_evento_registrado() {
        PasivoStrategy strategy = new PasivoStrategy(repository, ejecutor, contexto);

        strategy.procesar(evento("PASIVO"));

        assertThat(recibidosA).hasSize(1);
        assertThat(recibidosA.get(0).getEvento().getId()).isEqualTo(99L);
        assertThat(recibidosA.get(0).getEvento().getModo()).isEqualTo("PASIVO");
    }

    @Test
    @DisplayName("Guardar un evento ACTIVO emite EventoRegistradoEvent")
    void activo_emite_evento_registrado() {
        ActivoStrategy strategy = new ActivoStrategy(repository, new VisionAdapter(), ejecutor, contexto);
        RegistroEvento e = evento("ACTIVO");
        e.setEtiquetasIA("chair (80%)");

        strategy.procesar(e);

        assertThat(recibidosA).hasSize(1);
        assertThat(recibidosA.get(0).getEvento().getEtiquetasIA()).isEqualTo("Chair (80%)");
    }

    @Test
    @DisplayName("Todos los observers suscritos reciben el mismo evento")
    void varios_observers_reciben_el_evento() {
        new PasivoStrategy(repository, ejecutor, contexto).procesar(evento("PASIVO"));

        assertThat(recibidosA).hasSize(1);
        assertThat(recibidosB).hasSize(1);
        assertThat(recibidosA.get(0)).isSameAs(recibidosB.get(0));
    }

    @Test
    @DisplayName("LogEventoObserver recibe el evento publicado y delega en onEventoRegistrado")
    void log_observer_recibe_evento() {
        LogEventoObserver observer = spy(new LogEventoObserver());
        contexto.close();
        contexto = new GenericApplicationContext();
        contexto.addApplicationListener(observer);
        contexto.refresh();

        new PasivoStrategy(repository, ejecutor, contexto).procesar(evento("PASIVO"));

        verify(observer).onEventoRegistrado(any(EventoRegistradoEvent.class));
    }

    @Test
    @DisplayName("PASIVO: si falla la persistencia NO se emite el evento")
    void pasivo_no_emite_si_falla_persistencia() {
        lenient().when(repository.save(any(RegistroEvento.class))).thenThrow(new RuntimeException("db caída"));
        PasivoStrategy strategy = new PasivoStrategy(repository, ejecutor, contexto);

        assertThatThrownBy(() -> strategy.procesar(evento("PASIVO")))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("db caída");

        assertThat(recibidosA).isEmpty();
        assertThat(recibidosB).isEmpty();
    }

    @Test
    @DisplayName("ACTIVO: si falla la persistencia NO se emite el evento")
    void activo_no_emite_si_falla_persistencia() {
        lenient().when(repository.save(any(RegistroEvento.class))).thenThrow(new RuntimeException("db caída"));
        ActivoStrategy strategy = new ActivoStrategy(repository, new VisionAdapter(), ejecutor, contexto);

        assertThatThrownBy(() -> strategy.procesar(evento("ACTIVO")))
                .isInstanceOf(RuntimeException.class);

        assertThat(recibidosA).isEmpty();
    }

    @Test
    @DisplayName("EventoRegistradoEvent expone la entidad y su origen")
    void evento_expone_entidad_y_origen() {
        RegistroEvento e = evento("PASIVO");
        Object origen = new Object();

        EventoRegistradoEvent ev = new EventoRegistradoEvent(origen, e);

        assertThat(ev.getEvento()).isSameAs(e);
        assertThat(ev.getSource()).isSameAs(origen);
    }
}
