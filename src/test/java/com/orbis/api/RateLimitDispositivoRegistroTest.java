package com.orbis.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * El registro de bastones se limita por IP. El límite es bajo solo para esta prueba.
 * Un código inválido cuenta igual que uno válido: el límite frena la adivinación.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:ratelimit-dispositivo;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
                "app.ratelimit.dispositivo-registro.max-intentos=2",
                "app.ratelimit.dispositivo-registro.ventana-segundos=3600"
        })
@AutoConfigureMockMvc
class RateLimitDispositivoRegistroTest {

    @Autowired MockMvc mvc;

    private static final String CUERPO = "{\"codigo\":\"ZZZZ-ZZZZ\",\"modeloHardware\":\"ESP32-CAM\",\"versionFirmware\":\"0.1.0\"}";

    @Test
    @DisplayName("Tras el máximo de intentos por IP, /registro responde 429 con Retry-After")
    void limite_por_ip_en_registro_de_dispositivo() throws Exception {
        mvc.perform(post("/api/v1/dispositivos/registro").contentType("application/json").content(CUERPO))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/v1/dispositivos/registro").contentType("application/json").content(CUERPO))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/v1/dispositivos/registro").contentType("application/json").content(CUERPO))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }
}
