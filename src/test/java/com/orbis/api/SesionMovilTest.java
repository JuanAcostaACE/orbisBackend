package com.orbis.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbis.api.dto.LoginRequest;
import com.orbis.api.dto.RegistroRequest;
import com.orbis.api.model.SesionMovil;
import com.orbis.api.repository.SesionMovilRepository;
import com.orbis.api.service.DispositivoService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Inicio de sesión móvil con token de acceso (POST /auth/token, GET /auth/token/yo,
 * POST /auth/token/logout) y su separación respecto de los tokens de dispositivo y de la sesión web.
 * BD H2 propia.
 *
 * Nota: el número de POST a /auth/token y /auth/login en esta clase se mantiene por debajo del
 * límite por IP de login (10 por minuto) para no depender del orden de ejecución.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "spring.datasource.url=jdbc:h2:mem:sesion-movil;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@AutoConfigureMockMvc
class SesionMovilTest {

    static final String PASS = "contraseña_movil_123";

    @Autowired MockMvc mvc;
    @Autowired DispositivoService dispositivos;
    @Autowired SesionMovilRepository sesiones;
    final ObjectMapper mapper = new ObjectMapper();

    private String correoNuevo(String prefijo) throws Exception {
        String correo = prefijo + "-" + System.nanoTime() + "@test.com";
        mvc.perform(post("/auth/registro")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new RegistroRequest("Movil Test", correo, PASS))))
                .andExpect(status().isCreated());
        return correo;
    }

    private MvcResult loginMovil(String correo, String pass) throws Exception {
        return mvc.perform(post("/auth/token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new LoginRequest(correo, pass))))
                .andReturn();
    }

    private String tokenDe(MvcResult r) throws Exception {
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        JsonNode json = mapper.readTree(r.getResponse().getContentAsString());
        return json.get("accessToken").asText();
    }

    @Test
    @DisplayName("a) login correcto devuelve accessToken, tokenType Bearer y expiresIn 28800")
    void login_movil_correcto() throws Exception {
        String correo = correoNuevo("a");
        mvc.perform(post("/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new LoginRequest(correo, PASS))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(28800))
                .andExpect(jsonPath("$.correo").value(correo))
                .andExpect(jsonPath("$.nombre").value("Movil Test"));
    }

    @Test
    @DisplayName("b) contraseña incorrecta → 401 CREDENCIALES_INCORRECTAS")
    void login_movil_incorrecto() throws Exception {
        String correo = correoNuevo("b");
        mvc.perform(post("/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new LoginRequest(correo, "otra_contraseña_1")))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("CREDENCIALES_INCORRECTAS"));
    }

    @Test
    @DisplayName("c) /auth/token/yo con el token → 200 y correo; sin cabecera → 401")
    void yo_con_token_y_sin_cabecera() throws Exception {
        String correo = correoNuevo("c");
        String token = tokenDe(loginMovil(correo, PASS));

        mvc.perform(get("/auth/token/yo").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correo").value(correo));

        mvc.perform(get("/auth/token/yo")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("d) logout → 204 y después el mismo token responde 401 en /auth/token/yo")
    void logout_revoca_token() throws Exception {
        String correo = correoNuevo("d");
        String token = tokenDe(loginMovil(correo, PASS));

        mvc.perform(post("/auth/token/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mvc.perform(get("/auth/token/yo").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("e) el login por cookie (/auth/login) sigue igual: 200, sesión web con contexto de seguridad y sin accessToken")
    void login_cookie_sigue_funcionando() throws Exception {
        String correo = correoNuevo("e");
        MvcResult r = mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new LoginRequest(correo, PASS))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andReturn();
        MockHttpSession sesion = (MockHttpSession) r.getRequest().getSession(false);
        assertThat(sesion).isNotNull();
        assertThat(sesion.getAttribute("SPRING_SECURITY_CONTEXT")).isNotNull();

        mvc.perform(get("/auth/me").session(sesion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correo").value(correo));
    }

    @Test
    @DisplayName("f) un token de dispositivo NO autentica en /auth/token/yo ni en logout → 401")
    void token_dispositivo_no_es_sesion_movil() throws Exception {
        String correo = correoNuevo("f");
        String codigo = dispositivos.crearVinculacion(correo).codigo();
        String tokenDispositivo = dispositivos.registrar(codigo, "ESP32-CAM", "0.1.0").token();

        mvc.perform(get("/auth/token/yo").header("Authorization", "Bearer " + tokenDispositivo))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/auth/token/logout").header("Authorization", "Bearer " + tokenDispositivo))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("g) un token de sesión móvil NO autentica como dispositivo: rotar exige ROLE_DEVICE → 403, no 200")
    void token_movil_no_es_dispositivo() throws Exception {
        String correo = correoNuevo("g");
        String token = tokenDe(loginMovil(correo, PASS));

        mvc.perform(post("/api/v1/dispositivos/token/rotar").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("h) token caducado → 401")
    void token_caducado() throws Exception {
        String correo = correoNuevo("h");
        String token = tokenDe(loginMovil(correo, PASS));

        SesionMovil sesion = sesiones.findAll().stream()
                .filter(s -> s.getCorreo().equals(correo))
                .findFirst()
                .orElseThrow();
        sesion.setExpiraEn(LocalDateTime.now().minusMinutes(1));
        sesiones.save(sesion);

        mvc.perform(get("/auth/token/yo").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("El error de credenciales no devuelve token ni refleja la contraseña")
    void error_no_contiene_secretos() throws Exception {
        String correo = correoNuevo("x");
        MvcResult r = loginMovil(correo, "incorrecta_123");
        assertThat(r.getResponse().getStatus()).isEqualTo(401);
        assertThat(r.getResponse().getContentAsString())
                .doesNotContain("accessToken", "incorrecta_123")
                .contains("CREDENCIALES_INCORRECTAS");
    }
}
