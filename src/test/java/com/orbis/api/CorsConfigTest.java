package com.orbis.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Preflight CORS: solo orígenes, métodos y cabeceras explícitos.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CorsConfigTest {

    @Autowired MockMvc mvc;

    @Test
    @DisplayName("Origen permitido recibe Access-Control-Allow-Origin y credenciales")
    void allowed_origin() throws Exception {
        mvc.perform(options("/api/v1/events")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"))
                .andExpect(header().string("Access-Control-Allow-Methods", not(containsString("DELETE"))));
    }

    @Test
    @DisplayName("Origen desconocido es rechazado (403)")
    void unknown_origin() throws Exception {
        mvc.perform(options("/api/v1/events")
                        .header("Origin", "https://atacante.example")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    @DisplayName("Cabecera no listada es rechazada en el preflight")
    void header_not_allowed() throws Exception {
        mvc.perform(options("/api/v1/events")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "X-Inventada"))
                .andExpect(status().isForbidden());
    }
}
