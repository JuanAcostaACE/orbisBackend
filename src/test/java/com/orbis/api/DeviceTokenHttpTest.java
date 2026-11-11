package com.orbis.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbis.api.dto.RegistroRequest;
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
    static final String EVENTO = "{\"modo\":\"PASIVO\",\"distanciaCm\":30.0}";

    @Autowired MockMvc mvc;
    final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("POST /api/v1/eventos con device token válido → 201")
    void device_token_valido_crea_evento() throws Exception {
        // El propietario del dispositivo debe existir como usuario
        mvc.perform(post("/auth/registro")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new RegistroRequest(
                        "Dueno Dispositivo", "dueno-dispositivo@test.com", "contraseña_segura_123"))))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/v1/eventos")
                .contentType(MediaType.APPLICATION_JSON)
                .content(EVENTO)
                .header("X-Device-Token", TOKEN))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists());
    }

    @Test
    @DisplayName("POST /api/v1/eventos sin token ni sesión → 401")
    void sin_token_ni_sesion_401() throws Exception {
        mvc.perform(post("/api/v1/eventos")
                .contentType(MediaType.APPLICATION_JSON)
                .content(EVENTO))
                .andExpect(status().isUnauthorized());
    }
}
