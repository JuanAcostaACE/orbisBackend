package com.orbis.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Rutas inexistentes: 404 JSON con el formato de error de la aplicación (nunca 500); /health sigue público. */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:ruta-inexistente;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
                "app.ratelimit.enabled=false",
                "app.analisis.activo=false"
        })
@AutoConfigureMockMvc
class RutaInexistenteTest {

    @Autowired MockMvc mvc;

    @Test
    @DisplayName("Ruta inexistente autenticada -> 404 JSON, no 500")
    void ruta_inexistente_404() throws Exception {
        mvc.perform(get("/api/v1/no-existe").with(user("x@test.com").roles("USER")))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.mensaje").value("Recurso no encontrado"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("Ruta inexistente sin autenticar -> 401 JSON (sin filtrar si existe)")
    void ruta_inexistente_sin_auth_401() throws Exception {
        mvc.perform(get("/api/v1/no-existe"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("/health es público")
    void health_publico() throws Exception {
        mvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
