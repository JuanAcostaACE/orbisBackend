package com.orbis.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbis.api.dto.LoginRequest;
import com.orbis.api.dto.RegisterRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Limitador de peticiones por IP (RateLimitFilter) con límites bajos para la prueba.
 * Login: 2 intentos por ventana. Registro: 1 intento por ventana.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:rate-limit;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
                "app.ratelimit.login.max-attempts=2",
                "app.ratelimit.login.window-seconds=60",
                "app.ratelimit.register.max-attempts=1",
                "app.ratelimit.register.window-seconds=3600"
        })
@AutoConfigureMockMvc
class RateLimitHttpTest {

    @Autowired MockMvc mvc;
    final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("POST /auth/login supera el límite → 429 con JSON y Retry-After")
    void login_exceeds_limit_429() throws Exception {
        String responseBody = mapper.writeValueAsString(new LoginRequest("nadie@test.com", "contraseña_mala"));

        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(responseBody))
                    .andExpect(status().isUnauthorized());
        }

        mvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(responseBody))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("POST /auth/register supera el límite → 429")
    void register_exceeds_limit_429() throws Exception {
        mvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new RegisterRequest(
                        "Primero", "primero-rl@test.com", "contraseña_segura_123"))))
                .andExpect(status().isCreated());

        mvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new RegisterRequest(
                        "Segundo", "segundo-rl@test.com", "contraseña_segura_123"))))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status").value(429));
    }

    @Test
    @DisplayName("Las rutas no limitadas (GET /health) no se ven afectadas")
    void health_not_rate_limited() throws Exception {
        for (int i = 0; i < 5; i++) {
            mvc.perform(get("/health")).andExpect(status().isOk());
        }
    }
}
