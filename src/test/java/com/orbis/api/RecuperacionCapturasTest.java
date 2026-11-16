package com.orbis.api;

import com.orbis.api.model.CapturaFotografia;
import com.orbis.api.model.EstadoCaptura;
import com.orbis.api.repository.CapturaFotografiaRepository;
import com.orbis.api.service.CapturaService;
import com.orbis.api.service.RecuperacionCapturas;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/** Transiciones de estado y recuperación de capturas en EN_PROCESO tras reinicio (D-003). */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:recuperacion-capturas;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
class RecuperacionCapturasTest {

    static final String DUENO = "recuperacion-dueno@test.com";

    @Autowired CapturaService capturas;
    @Autowired CapturaFotografiaRepository repository;
    @Autowired RecuperacionCapturas recuperacion;

    private CapturaFotografia nueva(String id, byte relleno) {
        return capturas.recibir(DUENO, id, CapturaValidacionTest.jpeg(20 * 1024, relleno)).captura();
    }

    @Test
    @DisplayName("Transición válida se persiste y registra el motivo al fallar")
    void transicion_valida() {
        CapturaFotografia c = nueva("5a1e0c2d-3b4f-4c6a-9d8e-7f6a5b4c3d2e", (byte) 0x31);
        CapturaFotografia enProceso = capturas.transicionar(c.getId(), EstadoCaptura.EN_PROCESO, null);
        assertEquals(EstadoCaptura.EN_PROCESO, enProceso.getEstado());

        CapturaFotografia fallida = capturas.transicionar(c.getId(), EstadoCaptura.FALLIDA, "IA_NO_DISPONIBLE");
        assertEquals(EstadoCaptura.FALLIDA, fallida.getEstado());
        assertEquals("IA_NO_DISPONIBLE", repository.findById(c.getId()).orElseThrow().getMotivoFallo());
    }

    @Test
    @DisplayName("Transición no permitida lanza error y no modifica la captura")
    void transicion_invalida_no_modifica() {
        CapturaFotografia c = nueva("6b2f1d3e-4c5a-4d7b-8e9f-801a6b5c4d3e", (byte) 0x32);
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> capturas.transicionar(c.getId(), EstadoCaptura.ENTREGADA, null));
        assertTrue(e.getMessage().contains("RECIBIDA"));
        assertEquals(EstadoCaptura.RECIBIDA, repository.findById(c.getId()).orElseThrow().getEstado());
    }

    @Test
    @DisplayName("Tras reinicio, capturas en EN_PROCESO pasan a FALLIDA con motivo REINICIO_DURANTE_PROCESO")
    void recuperacion_tras_reinicio() {
        CapturaFotografia c = nueva("7c3a2e4f-5d6b-4e8c-9fa0-912b7c6d5e4f", (byte) 0x33);
        capturas.transicionar(c.getId(), EstadoCaptura.EN_PROCESO, null);

        int marcadas = recuperacion.recuperar();
        assertTrue(marcadas >= 1);

        CapturaFotografia despues = repository.findById(c.getId()).orElseThrow();
        assertEquals(EstadoCaptura.FALLIDA, despues.getEstado());
        assertEquals(RecuperacionCapturas.MOTIVO_REINICIO, despues.getMotivoFallo());
    }

    @Test
    @DisplayName("Las capturas recibidas (no en proceso) no se tocan en la recuperación")
    void recuperacion_no_toca_recibidas() {
        CapturaFotografia c = nueva("8d4b3f50-6e7c-4f9d-8a1b-a23c8d7e6f50", (byte) 0x34);
        recuperacion.recuperar();
        assertEquals(EstadoCaptura.RECIBIDA, repository.findById(c.getId()).orElseThrow().getEstado());
    }
}
