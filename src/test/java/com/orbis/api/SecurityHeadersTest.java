package com.orbis.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifica que la API envía las cabeceras de seguridad configuradas en SecurityConfig.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SecurityHeadersTest {

    @Autowired MockMvc mvc;

    @Test
    @DisplayName("GET /health incluye nosniff, Referrer-Policy, Permissions-Policy y CSP")
    void cabeceras_basicas() throws Exception {
        mvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("Permissions-Policy", containsString("camera=()")))
                .andExpect(header().string("Content-Security-Policy", containsString("object-src 'none'")))
                .andExpect(header().string("X-Frame-Options", "SAMEORIGIN"));
    }

    @Test
    @DisplayName("Sobre HTTPS se envía Strict-Transport-Security")
    void hsts_en_https() throws Exception {
        mvc.perform(get("/health").secure(true))
                .andExpect(header().string("Strict-Transport-Security",
                        containsString("max-age=31536000")));
    }
}
