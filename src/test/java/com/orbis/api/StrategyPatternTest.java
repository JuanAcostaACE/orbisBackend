package com.orbis.api;

import com.orbis.api.command.EjecutorComando;
import com.orbis.api.facade.VisionAdapter;
import com.orbis.api.facade.VisionException;
import com.orbis.api.facade.VisionFacade;
import com.orbis.api.model.RegistroEvento;
import com.orbis.api.observer.EventoRegistradoEvent;
import com.orbis.api.repository.RegistroEventoRepository;
import com.orbis.api.strategy.ActivoStrategy;
import com.orbis.api.strategy.PasivoStrategy;
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
 *  - PasivoStrategy: guarda telemetría sin tocar IA.
 *  - ActivoStrategy: usa la fachada/adaptador de visión y guarda etiquetas normalizadas.
 */
@ExtendWith(MockitoExtension.class)
class StrategyPatternTest {

    @Mock RegistroEventoRepository repository;
    @Mock ApplicationEventPublisher publisher;
    @Mock VisionFacade visionFacade;

    private final EjecutorComando ejecutor = new EjecutorComando();

    @BeforeEach
    void setUp() {
        // El repositorio devuelve la misma entidad que recibe
        lenient().when(repository.save(any(RegistroEvento.class)))
                 .thenAnswer(inv -> inv.getArgument(0));
    }

    private static RegistroEvento evento(String modo, double distancia, String etiquetas) {
        RegistroEvento e = new RegistroEvento();
        e.setModo(modo);
        e.setDistanciaCm(distancia);
        e.setEtiquetasIA(etiquetas);
        return e;
    }

    // ── PASIVO ────────────────────────────────────────────────────

    @Test
    @DisplayName("PasivoStrategy guarda el evento y no usa IA (no depende de VisionFacade)")
    void pasivo_guarda_sin_ia() {
        PasivoStrategy strategy = new PasivoStrategy(repository, ejecutor, publisher);
        RegistroEvento entrada = evento("PASIVO", 42.0, null);

        RegistroEvento resultado = strategy.procesar(entrada);

        verify(repository).save(entrada);
        assertThat(resultado.getEtiquetasIA()).isNull();
        verifyNoInteractions(visionFacade);
        // Garantía estructural: la estrategia ni siquiera recibe una fachada de visión
        assertThat(PasivoStrategy.class.getConstructors()[0].getParameterTypes())
                .doesNotContain(VisionFacade.class);
    }

    @Test
    @DisplayName("PasivoStrategy publica EventoRegistradoEvent tras guardar")
    void pasivo_publica_evento() {
        PasivoStrategy strategy = new PasivoStrategy(repository, ejecutor, publisher);

        strategy.procesar(evento("PASIVO", 30.0, null));

        verify(publisher).publishEvent(any(EventoRegistradoEvent.class));
    }

    // ── ACTIVO ────────────────────────────────────────────────────

    @Test
    @DisplayName("ActivoStrategy normaliza y filtra etiquetas del cliente con el VisionAdapter real")
    void activo_normaliza_etiquetas_con_adapter_real() {
        ActivoStrategy strategy = new ActivoStrategy(repository, new VisionAdapter(), ejecutor, publisher);
        RegistroEvento entrada = evento("ACTIVO", 60.0, "chair (85%), noise (30%), person (92%)");

        RegistroEvento resultado = strategy.procesar(entrada);

        // "noise (30%)" cae bajo el umbral de confianza (40%) y se capitaliza el resto
        assertThat(resultado.getEtiquetasIA()).isEqualTo("Chair (85%), Person (92%)");
        verify(publisher).publishEvent(any(EventoRegistradoEvent.class));
    }

    @Test
    @DisplayName("ActivoStrategy pasa las etiquetas por la fachada y guarda lo que devuelve")
    void activo_usa_fachada_para_etiquetas() {
        when(visionFacade.analizarImagen("laptop, mesa")).thenReturn(List.of("Laptop", "Mesa"));
        ActivoStrategy strategy = new ActivoStrategy(repository, visionFacade, ejecutor, publisher);

        RegistroEvento resultado = strategy.procesar(evento("ACTIVO", 50.0, "laptop, mesa"));

        verify(visionFacade).analizarImagen("laptop, mesa");
        assertThat(resultado.getEtiquetasIA()).isEqualTo("Laptop, Mesa");
    }

    @Test
    @DisplayName("ActivoStrategy: VisionException -> ERROR_VISION y el evento se guarda igualmente")
    void activo_vision_exception_no_rechaza_evento() {
        when(visionFacade.analizarImagen(anyString())).thenThrow(new VisionException("boom"));
        ActivoStrategy strategy = new ActivoStrategy(repository, visionFacade, ejecutor, publisher);

        RegistroEvento resultado = strategy.procesar(evento("ACTIVO", 50.0, "algo"));

        assertThat(resultado.getEtiquetasIA()).isEqualTo("ERROR_VISION: boom");
        ArgumentCaptor<RegistroEvento> captor = ArgumentCaptor.forClass(RegistroEvento.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getEtiquetasIA()).startsWith("ERROR_VISION");
    }

    @Test
    @DisplayName("ActivoStrategy: flujo imagen Base64 consulta la fachada mediante ComandoConsultarIA")
    void activo_flujo_imagen_base64() {
        RegistroEvento entrada = evento("ACTIVO", 40.0, null);
        entrada.setImagenUrl("BASE64DATA");
        when(visionFacade.analizarImagen("BASE64DATA")).thenReturn(List.of("Chair"));
        ActivoStrategy strategy = new ActivoStrategy(repository, visionFacade, ejecutor, publisher);

        RegistroEvento resultado = strategy.procesar(entrada);

        verify(visionFacade).analizarImagen("BASE64DATA");
        assertThat(resultado.getEtiquetasIA()).isEqualTo("Chair");
    }

    @Test
    @DisplayName("ActivoStrategy: sin imagen ni etiquetas -> SIN_IMAGEN y sin llamar a la fachada")
    void activo_sin_imagen_ni_etiquetas() {
        ActivoStrategy strategy = new ActivoStrategy(repository, visionFacade, ejecutor, publisher);

        RegistroEvento resultado = strategy.procesar(evento("ACTIVO", 40.0, null));

        assertThat(resultado.getEtiquetasIA()).isEqualTo("SIN_IMAGEN");
        verify(visionFacade, never()).analizarImagen(anyString());
    }
}
