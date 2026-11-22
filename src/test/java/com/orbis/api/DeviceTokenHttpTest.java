package com.orbis.api;

import com.fasterxml.jackson.databind.ObjectMapper;
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
 * Autenticación de dispositivo (ESP32) por cabecera X-Device-Token.
 * Token y correo propietario se configuran solo para esta clase de prueba.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:device-token;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
                "app.device.token=token-de-prueba-no-real-0123456789",
                "app.device.ownerEmail=dueno-dispositivo@test.com"
        })
@AutoConfigureMockMvc
class DeviceTokenHttpTest {

    static final String TOKEN = "token-de-prueba-no-real-0123456789";
    static final String EVENT = "{\"mode\":\"PASSIVE\",\"distanceCm\":30.0}";

    @Autowired MockMvc mvc;
    final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("POST /api/v1/events con device token válido → 201")
    void device_token_valid_creates_event() throws Exception {
        // El propietario del dispositivo debe existir como usuario
        mvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new RegisterRequest(
                        "Dueno Dispositivo", "dueno-dispositivo@test.com", "contraseña_segura_123"))))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/v1/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(EVENT)
                .header("X-Device-Token", TOKEN))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists());
    }

    @Test
    @DisplayName("POST /api/v1/events sin token ni sesión → 401")
    void no_token_or_session_401() throws Exception {
        mvc.perform(post("/api/v1/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(EVENT))
                .andExpect(status().isUnauthorized());
    }
}
