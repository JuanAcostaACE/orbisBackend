package com.orbis.api;

import com.orbis.api.model.RegistroEvento;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    @Autowired private ObjectMapper objectMapper;

    @Test @DisplayName("POST PASIVO -> 200")
    void postPasivo() throws Exception {
        RegistroEvento r = new RegistroEvento();
        r.setModo("PASIVO"); r.setDistanciaCm(35.0);
        mockMvc.perform(post("/api/v1/eventos").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(r)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").exists());
    }

    @Test @DisplayName("POST ACTIVO sin imagen -> SIN_IMAGEN")
    void postActivo() throws Exception {
        RegistroEvento r = new RegistroEvento();
        r.setModo("ACTIVO"); r.setDistanciaCm(28.0);
        mockMvc.perform(post("/api/v1/eventos").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(r)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.etiquetasIA").value("SIN_IMAGEN"));
    }

    @Test @DisplayName("Modo invalido -> 400")
    void postInvalido() throws Exception {
        RegistroEvento r = new RegistroEvento(); r.setModo("X");
        mockMvc.perform(post("/api/v1/eventos").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(r)))
                .andExpect(status().isBadRequest());
    }

    @Test @DisplayName("GET /eventos/estado -> State pattern")
    void getEstado() throws Exception {
        mockMvc.perform(get("/api/v1/eventos/estado")).andExpect(status().isOk())
                .andExpect(jsonPath("$.estadoActual").exists());
    }

    @Test @DisplayName("GET /health -> UP")
    void health() throws Exception {
        mockMvc.perform(get("/health")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
