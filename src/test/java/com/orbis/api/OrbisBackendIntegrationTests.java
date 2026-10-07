package com.orbis.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class OrbisBackendIntegrationTests {

    @Autowired private MockMvc mockMvc;

    @Test @DisplayName("POST PASIVO -> 200")
    void postPasivo() throws Exception {
        String body = "{\"modo\":\"PASIVO\",\"distanciaCm\":35.0}";
        mockMvc.perform(post("/api/v1/eventos")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists());
    }

    @Test @DisplayName("POST ACTIVO sin imagen -> SIN_IMAGEN")
    void postActivo() throws Exception {
        String body = "{\"modo\":\"ACTIVO\",\"distanciaCm\":28.0}";
        mockMvc.perform(post("/api/v1/eventos")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.etiquetasIA").value("SIN_IMAGEN"));
    }

    @Test @DisplayName("Modo invalido -> 400")
    void postInvalido() throws Exception {
        String body = "{\"modo\":\"X\"}";
        mockMvc.perform(post("/api/v1/eventos")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test @DisplayName("GET /eventos/estado -> State pattern")
    void getEstado() throws Exception {
        mockMvc.perform(get("/api/v1/eventos/estado"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estadoActual").exists());
    }

    @Test @DisplayName("GET /health -> UP")
    void health() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}