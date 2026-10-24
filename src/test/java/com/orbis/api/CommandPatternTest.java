package com.orbis.api;

import com.orbis.api.command.Comando;
import com.orbis.api.command.ComandoConsultarIA;
import com.orbis.api.command.ComandoGuardarEvento;
import com.orbis.api.command.EjecutorComando;
import com.orbis.api.facade.VisionException;
import com.orbis.api.facade.VisionFacade;
import com.orbis.api.model.RegistroEvento;
import com.orbis.api.repository.RegistroEventoRepository;
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
 * Patrón COMMAND: EjecutorComando (invoker), ComandoGuardarEvento y ComandoConsultarIA
 * (comandos concretos) con sus dependencias aisladas por mocks.
 */
@ExtendWith(MockitoExtension.class)
class CommandPatternTest {

    @Mock RegistroEventoRepository repository;
    @Mock VisionFacade visionFacade;

    private final EjecutorComando ejecutor = new EjecutorComando();

    // ── EjecutorComando ───────────────────────────────────────────

    @Test
    @DisplayName("EjecutorComando ejecuta el comando una vez y devuelve su resultado")
    void ejecutor_ejecuta_y_devuelve_resultado() {
        AtomicInteger llamadas = new AtomicInteger();
        Comando<String> comando = () -> { llamadas.incrementAndGet(); return "ok"; };

        String resultado = ejecutor.ejecutar(comando);

        assertThat(resultado).isEqualTo("ok");
        assertThat(llamadas.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("EjecutorComando propaga las excepciones del comando")
    void ejecutor_propaga_excepciones() {
        Comando<String> comando = () -> { throw new IllegalStateException("falló"); };

        assertThatThrownBy(() -> ejecutor.ejecutar(comando))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("falló");
    }

    // ── ComandoGuardarEvento ──────────────────────────────────────

    @Test
    @DisplayName("ComandoGuardarEvento: no toca el repositorio hasta ejecutar() y luego guarda")
    void guardar_es_diferido_y_persiste() {
        RegistroEvento entrada = new RegistroEvento();
        entrada.setModo("PASIVO");
        RegistroEvento guardado = new RegistroEvento();
        guardado.setId(7L);
        when(repository.save(entrada)).thenReturn(guardado);

        ComandoGuardarEvento comando = new ComandoGuardarEvento(repository, entrada);
        verifyNoInteractions(repository);              // construir el comando no ejecuta nada

        RegistroEvento resultado = ejecutor.ejecutar(comando);

        verify(repository).save(entrada);
        assertThat(resultado).isSameAs(guardado);
        assertThat(resultado.getId()).isEqualTo(7L);
    }

    @Test
    @DisplayName("ComandoGuardarEvento propaga el fallo de persistencia")
    void guardar_propaga_fallo() {
        RegistroEvento entrada = new RegistroEvento();
        when(repository.save(entrada)).thenThrow(new RuntimeException("db caída"));

        assertThatThrownBy(() -> ejecutor.ejecutar(new ComandoGuardarEvento(repository, entrada)))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("db caída");
    }

    // ── ComandoConsultarIA ────────────────────────────────────────

    @Test
    @DisplayName("ComandoConsultarIA delega en la fachada con el mismo argumento")
    void consultar_ia_delega_en_fachada() {
        when(visionFacade.analizarImagen("chair, table")).thenReturn(List.of("Chair", "Table"));

        List<String> resultado = ejecutor.ejecutar(new ComandoConsultarIA(visionFacade, "chair, table"));

        verify(visionFacade).analizarImagen("chair, table");
        assertThat(resultado).containsExactly("Chair", "Table");
    }

    @Test
    @DisplayName("ComandoConsultarIA no consulta la fachada hasta ejecutar()")
    void consultar_ia_es_diferido() {
        new ComandoConsultarIA(visionFacade, "x");
        verifyNoInteractions(visionFacade);
    }

    @Test
    @DisplayName("ComandoConsultarIA propaga VisionException a través del ejecutor")
    void consultar_ia_propaga_vision_exception() {
        when(visionFacade.analizarImagen("mal")).thenThrow(new VisionException("entrada inválida"));

        assertThatThrownBy(() -> ejecutor.ejecutar(new ComandoConsultarIA(visionFacade, "mal")))
                .isInstanceOf(VisionException.class)
                .hasMessage("entrada inválida");
    }
}
