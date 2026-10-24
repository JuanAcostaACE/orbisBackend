package com.orbis.api;

import com.orbis.api.command.ComandoConsultarIA;
import com.orbis.api.command.EjecutorComando;
import com.orbis.api.facade.VisionAdapter;
import com.orbis.api.facade.VisionException;
import com.orbis.api.facade.VisionFacade;
import com.orbis.api.model.RegistroEvento;
import com.orbis.api.repository.RegistroEventoRepository;
import com.orbis.api.strategy.ActivoStrategy;
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
    @Mock RegistroEventoRepository repository;
    @Mock ApplicationEventPublisher publisher;

    @Test
    @DisplayName("VisionAdapter implementa VisionFacade")
    void adapter_implementa_facade() {
        assertThat(new VisionAdapter()).isInstanceOf(VisionFacade.class);
    }

    @Test
    @DisplayName("A través de la interfaz: valida, normaliza y filtra por umbral")
    void facade_normaliza_via_interfaz() {
        VisionFacade facade = new VisionAdapter();

        List<String> resultado = facade.analizarImagen("person (92%), noise (10%), chair");

        assertThat(resultado).containsExactly("Person (92%)", "Chair");
    }

    @Test
    @DisplayName("Entrada corrupta -> VisionException a través de la interfaz")
    void facade_propaga_vision_exception() {
        VisionFacade facade = new VisionAdapter();

        assertThatThrownBy(() -> facade.analizarImagen(null)).isInstanceOf(VisionException.class);
        assertThatThrownBy(() -> facade.analizarImagen("ERROR_IA: fallo")).isInstanceOf(VisionException.class);
        assertThatThrownBy(() -> facade.analizarImagen("data:image/png;base64,AAAA")).isInstanceOf(VisionException.class);
        assertThatThrownBy(() -> facade.analizarImagen("<img src=x onerror=alert(1)>")).isInstanceOf(VisionException.class);
    }

    @Test
    @DisplayName("Todas las etiquetas bajo el umbral -> lista vacía (sin excepción)")
    void facade_todo_bajo_umbral_devuelve_vacio() {
        assertThat(new VisionAdapter().analizarImagen("Noise (10%), Blur (20%)")).isEmpty();
    }

    @Test
    @DisplayName("ActivoStrategy + VisionAdapter real: etiqueta corrupta -> ERROR_VISION y se guarda")
    void estrategia_activa_maneja_vision_exception_real() {
        lenient().when(repository.save(any(RegistroEvento.class))).thenAnswer(inv -> inv.getArgument(0));
        ActivoStrategy strategy = new ActivoStrategy(repository, new VisionAdapter(), new EjecutorComando(), publisher);
        RegistroEvento e = new RegistroEvento();
        e.setModo("ACTIVO");
        e.setDistanciaCm(35.0);
        e.setEtiquetasIA("<script>alert(1)</script>");

        RegistroEvento resultado = strategy.procesar(e);

        assertThat(resultado.getEtiquetasIA()).startsWith("ERROR_VISION:");
        verify(repository).save(e);
    }

    @Test
    @DisplayName("ComandoConsultarIA coordina con la fachada y propaga VisionException")
    void comando_consultar_ia_propaga_error() {
        when(facadeMock.analizarImagen("x")).thenThrow(new VisionException("fallo de visión"));
        ComandoConsultarIA comando = new ComandoConsultarIA(facadeMock, "x");

        assertThatThrownBy(comando::ejecutar)
                .isInstanceOf(VisionException.class)
                .hasMessage("fallo de visión");
    }

    @Test
    @DisplayName("VisionException conserva la causa original")
    void vision_exception_con_causa() {
        Throwable causa = new IllegalStateException("origen");
        VisionException ex = new VisionException("envoltorio", causa);

        assertThat(ex).hasMessage("envoltorio").hasCause(causa);
    }
}
