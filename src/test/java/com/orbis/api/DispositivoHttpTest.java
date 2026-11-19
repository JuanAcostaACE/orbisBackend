package com.orbis.api;

import com.orbis.api.repository.RegistroEventoRepository;
import com.orbis.api.service.CapturaService;
import com.orbis.api.service.DispositivoService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Flujo HTTP completo del bastón con token propio: registro, config, captura, evento, rotación. */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:dispositivo-http;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
                "app.analisis.activo=false"
        })
@AutoConfigureMockMvc
class DispositivoHttpTest {

    static final String DUENO = "dispositivo-http-dueno@test.com";
    static final String CAPTURA_ID = "a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d";

    @Autowired MockMvc mvc;
    @Autowired DispositivoService servicio;
    @Autowired CapturaService capturas;
    @Autowired RegistroEventoRepository eventos;

    static byte[] jpeg() {
        return CapturaValidacionTest.jpeg(20 * 1024, (byte) 0x2A);
    }

    private String registrarBaston() {
        String codigo = servicio.crearVinculacion(DUENO).codigo();
        return servicio.registrar(codigo, "ESP32-CAM", "0.1.0").token();
    }

    @Test
    @DisplayName("POST /registro con código válido → 201 con token; segundo uso → 409")
    void registro_http() throws Exception {
        String codigo = servicio.crearVinculacion(DUENO).codigo();
        mvc.perform(post("/api/v1/dispositivos/registro")
                        .contentType("application/json")
                        .content("{\"codigo\":\"" + codigo + "\",\"modeloHardware\":\"ESP32-CAM\",\"versionFirmware\":\"0.1.0\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.deviceToken").isString())
                .andExpect(jsonPath("$.deviceId").isString());

        mvc.perform(post("/api/v1/dispositivos/registro")
                        .contentType("application/json")
                        .content("{\"codigo\":\"" + codigo + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CODIGO_NO_VALIDO"));
    }

    @Test
    @DisplayName("GET /config: con token propio → 200; sin token o con token falso → 401")
    void config_con_token_propio() throws Exception {
        String token = registrarBaston();
        mvc.perform(get("/api/v1/dispositivos/config").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resolucion").value("VGA"));

        mvc.perform(get("/api/v1/dispositivos/config"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/dispositivos/config").header("Authorization", "Bearer falso"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Captura con token propio → 202 y la captura queda a nombre del propietario")
    void captura_con_token_propio() throws Exception {
        String token = registrarBaston();
        mvc.perform(post("/api/v1/dispositivos/capturas")
                        .contentType("image/jpeg")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Capture-Id", CAPTURA_ID)
                        .content(jpeg()))
                .andExpect(status().isAccepted());
        assertTrue(capturas.obtener(DUENO, CAPTURA_ID).isPresent());
    }

    @Test
    @DisplayName("Evento con token propio → 201, y el propietario es el usuario, nunca 'device:...'")
    @org.springframework.transaction.annotation.Transactional // propietario es LAZY: leerlo requiere sesión
    void evento_con_token_propio_usa_propietario_real() throws Exception {
        // El propietario debe existir como usuario: el servicio de eventos lo busca por correo.
        mvc.perform(post("/auth/registro")
                        .contentType("application/json")
                        .content(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(
                                new com.orbis.api.dto.RegistroRequest(
                                        "Dueno HTTP", DUENO, "contraseña_segura_123"))))
                .andExpect(status().isCreated());
        String token = registrarBaston();
        mvc.perform(post("/api/v1/eventos")
                        .contentType("application/json")
                        .header("Authorization", "Bearer " + token)
                        .content("{\"modo\":\"PASIVO\",\"distanciaCm\":42.0}"))
                .andExpect(status().isCreated());

        eventos.findAll().forEach(e -> {
            if (e.getPropietario() != null) {
                assertFalse(e.getPropietario().getCorreo().startsWith("device:"),
                        "un evento de bastón nunca debe quedar a nombre de 'device:...'");
            }
        });
    }

    @Test
    @DisplayName("Tras rotar, el token anterior responde 401 y el nuevo funciona")
    void rotacion_http() throws Exception {
        String viejo = registrarBaston();
        String respuesta = mvc.perform(post("/api/v1/dispositivos/token/rotar")
                        .header("Authorization", "Bearer " + viejo))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String nuevo = respuesta.replaceAll(".*\"deviceToken\":\"([^\"]+)\".*", "$1");

        mvc.perform(get("/api/v1/dispositivos/config").header("Authorization", "Bearer " + viejo))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/dispositivos/config").header("Authorization", "Bearer " + nuevo))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("El token por dispositivo no puede llamar a la rotación sin ser dispositivo: sin token → 401")
    void rotacion_sin_token() throws Exception {
        mvc.perform(post("/api/v1/dispositivos/token/rotar"))
                .andExpect(status().isUnauthorized());
    }
}
