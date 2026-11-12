package com.orbis.api;

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
 * Peticiones POST sin cuerpo en /auth/registro y /auth/login.
 * Deben responder 400 con el formato de error de la aplicación, nunca 500.
 * Límite de peticiones desactivado para que no interfiera con la prueba.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:peticion-sin-cuerpo;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
                "app.ratelimit.enabled=false"
        })
@AutoConfigureMockMvc
class PeticionSinCuerpoTest {

    @Autowired MockMvc mvc;

    @Test
    @DisplayName("POST /auth/registro sin cuerpo → 400 con formato de error")
    void registro_sin_cuerpo_400() throws Exception {
        mvc.perform(post("/auth/registro")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.mensaje").value("Cuerpo de la petición inválido o vacío"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("POST /auth/login sin cuerpo → 400 con formato de error")
    void login_sin_cuerpo_400() throws Exception {
        mvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.mensaje").value("Cuerpo de la petición inválido o vacío"))
                .andExpect(jsonPath("$.timestamp").exists());
    }
}
