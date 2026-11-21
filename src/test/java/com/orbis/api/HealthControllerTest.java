package com.orbis.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /health es el endpoint que usa Railway para saber si el servicio vive.
 */
@SpringBootTest
@AutoConfigureMockMvc
class HealthControllerTest {

    @Autowired MockMvc mvc;

    @Test
    @DisplayName("GET /health sin sesión → 200 con status UP")
    void health_public() throws Exception {
        mvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("GET /health identifica el servicio e incluye la hora")
    void health_body() throws Exception {
        mvc.perform(get("/health"))
                .andExpect(jsonPath("$.service").value("ORBIS Backend"))
                // LocalDateTime.toString(): 2026-11-20T10:00:00(.nnn)
                .andExpect(jsonPath("$.timestamp").value(
                        org.hamcrest.Matchers.matchesPattern("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}.*")));
    }

    @Test
    @DisplayName("POST /health no está permitido → 405 en JSON")
    void health_get_only() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/health"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405));
    }
}
