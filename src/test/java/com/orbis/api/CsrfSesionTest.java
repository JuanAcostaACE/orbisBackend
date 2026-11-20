package com.orbis.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Regresión: con sesión iniciada, un POST protegido por CSRF debe aceptar el token
 * enviado en la cabecera X-XSRF-TOKEN. Antes respondía 403 aunque el token fuera correcto.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "spring.datasource.url=jdbc:h2:mem:csrf-sesion;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@AutoConfigureMockMvc
class CsrfSesionTest {

    @Autowired MockMvc mvc;

    @Test
    @DisplayName("POST vinculaciones con sesion y token CSRF valido en X-XSRF-TOKEN → 201")
    void vinculacion_con_csrf_valido() throws Exception {
        mvc.perform(post("/api/v1/dispositivos/vinculaciones")
                        .with(SecurityMockMvcRequestPostProcessors.user("csrf-prueba@test.com").roles("USER"))
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.codigo").isString());
    }

    @Test
    @DisplayName("POST vinculaciones sin token CSRF → 403")
    void vinculacion_sin_csrf_rechazada() throws Exception {
        mvc.perform(post("/api/v1/dispositivos/vinculaciones")
                        .with(SecurityMockMvcRequestPostProcessors.user("csrf-prueba@test.com").roles("USER")))
                .andExpect(status().isForbidden());
    }
}
