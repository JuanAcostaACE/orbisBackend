package com.smartcane.api;

import com.smartcane.api.model.RegistroEvento;
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

/**
 * Tests de integración del backend SmartCane AI.
 * Usan H2 en memoria — no requieren Railway ni credenciales externas.
 * Validan que los 6 patrones de diseño funcionan correctamente en conjunto.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SmartCaneIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // ─── Tests Strategy + Command + Observer + State ───────────────────

    @Test
    @DisplayName("POST modo PASIVO → 200 OK, evento guardado con id")
    void postModoPasivo_debeGuardarEvento() throws Exception {
        RegistroEvento request = new RegistroEvento();
        request.setModo("PASIVO");
        request.setDistanciaCm(35.0);

        mockMvc.perform(post("/api/v1/eventos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.modo").value("PASIVO"))
                .andExpect(jsonPath("$.distanciaCm").value(35.0))
                .andExpect(jsonPath("$.fechaHora").exists());
    }

    @Test
    @DisplayName("POST modo ACTIVO sin imagen → 200 OK, etiquetasIA = SIN_IMAGEN")
    void postModoActivo_sinImagen_debeGuardarConSinImagen() throws Exception {
        RegistroEvento request = new RegistroEvento();
        request.setModo("ACTIVO");
        request.setDistanciaCm(28.0);
        // No se envía imagenUrl → VisionFacade no se llama

        mockMvc.perform(post("/api/v1/eventos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modo").value("ACTIVO"))
                .andExpect(jsonPath("$.etiquetasIA").value("SIN_IMAGEN"));
    }

    @Test
    @DisplayName("POST modo inválido → 400 Bad Request con mensaje JSON")
    void postModoInvalido_debe400ConMensaje() throws Exception {
        RegistroEvento request = new RegistroEvento();
        request.setModo("MODO_QUE_NO_EXISTE");

        mockMvc.perform(post("/api/v1/eventos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.mensaje").exists())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("GET /eventos → 200 OK, lista (puede estar vacía)")
    void getEventos_debeRetornarLista() throws Exception {
        mockMvc.perform(get("/api/v1/eventos"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));
    }

    @Test
    @DisplayName("GET /eventos/{id} no existente → 404 Not Found")
    void getEventoPorId_noExistente_debe404() throws Exception {
        mockMvc.perform(get("/api/v1/eventos/99999"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /eventos/estado → 200 OK, estadoActual presente (State pattern)")
    void getEstado_debeRetornarEstadoActual() throws Exception {
        mockMvc.perform(get("/api/v1/eventos/estado"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estadoActual").exists());
    }

    @Test
    @DisplayName("GET /health → 200 OK (liveness probe)")
    void healthCheck_debeEstarDisponible() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("State: INACTIVO → PASIVO → ACTIVO → INACTIVO (transición completa)")
    void statePattern_transicionCompleta() throws Exception {
        // Verificar estado inicial
        mockMvc.perform(get("/api/v1/eventos/estado"))
                .andExpect(jsonPath("$.estadoActual").value("INACTIVO"));

        // POST PASIVO → transiciona a PASIVO
        RegistroEvento pasivoReq = new RegistroEvento();
        pasivoReq.setModo("PASIVO");
        pasivoReq.setDistanciaCm(20.0);
        mockMvc.perform(post("/api/v1/eventos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(pasivoReq)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/eventos/estado"))
                .andExpect(jsonPath("$.estadoActual").value("PASIVO"));

        // POST ACTIVO → transiciona PASIVO → ACTIVO → INACTIVO
        RegistroEvento activoReq = new RegistroEvento();
        activoReq.setModo("ACTIVO");
        activoReq.setDistanciaCm(20.0);
        mockMvc.perform(post("/api/v1/eventos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(activoReq)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/eventos/estado"))
                .andExpect(jsonPath("$.estadoActual").value("INACTIVO"));
    }
}
