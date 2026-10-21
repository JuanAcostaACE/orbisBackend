package com.orbis.api;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.orbis.api.dto.LoginRequest;
import com.orbis.api.dto.RegistroRequest;
import com.orbis.api.model.Usuario;
import com.orbis.api.repository.UsuarioRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests de integración para autenticación y autorización.
 *
 * Verifica:
 *  - Registro: 201, correo duplicado 400, campos inválidos 400.
 *  - Login: credenciales correctas 200, incorrectas 401.
 *  - Acceso anónimo a /api/v1/eventos → 401.
 *  - Acceso con sesión a /api/v1/eventos → 200 (paginado).
 *  - Aislamiento: usuario A no ve eventos de usuario B.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuthControllerTest {

    @Autowired MockMvc         mvc;
    @Autowired ObjectMapper    mapper;
    @Autowired UsuarioRepository usuarioRepo;
    @Autowired PasswordEncoder passwordEncoder;

    static final String CORREO_A = "usuarioa@test.com";
    static final String CORREO_B = "usuariob@test.com";
    static final String PASS     = "contraseña_segura_123";

    // ── Registro ──────────────────────────────────────────────────

    @Test @Order(1)
    @DisplayName("POST /auth/registro → 201 con datos válidos")
    void registro_valido() throws Exception {
        mvc.perform(post("/auth/registro")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(
                        new RegistroRequest("Usuario A", CORREO_A, PASS))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.correo").value(CORREO_A))
                .andExpect(jsonPath("$.mensaje").value("Registro exitoso"));
    }

    @Test @Order(2)
    @DisplayName("POST /auth/registro → 400 si correo ya existe")
    void registro_duplicado() throws Exception {
        // El usuario A ya existe del test anterior
        mvc.perform(post("/auth/registro")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(
                        new RegistroRequest("Duplicado", CORREO_A, PASS))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").exists());
    }

    @Test @Order(3)
    @DisplayName("POST /auth/registro → 400 si contraseña < 8 chars")
    void registro_contrasena_corta() throws Exception {
        mvc.perform(post("/auth/registro")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(
                        new RegistroRequest("Test", "nuevo@test.com", "123"))))
                .andExpect(status().isBadRequest());
    }

    @Test @Order(4)
    @DisplayName("POST /auth/registro → 400 si correo inválido")
    void registro_correo_invalido() throws Exception {
        mvc.perform(post("/auth/registro")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(
                        new RegistroRequest("Test", "no-es-correo", PASS))))
                .andExpect(status().isBadRequest());
    }

    // ── Login ─────────────────────────────────────────────────────

    @Test @Order(5)
    @DisplayName("POST /auth/login → 200 con credenciales correctas")
    void login_valido() throws Exception {
        mvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(
                        new LoginRequest(CORREO_A, PASS))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correo").value(CORREO_A));
    }

    @Test @Order(6)
    @DisplayName("POST /auth/login → 401 con contraseña incorrecta")
    void login_credenciales_incorrectas() throws Exception {
        mvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(
                        new LoginRequest(CORREO_A, "contraseña_mala"))))
                .andExpect(status().isUnauthorized());
    }

    // ── Acceso anónimo ────────────────────────────────────────────

    @Test @Order(7)
    @DisplayName("GET /api/v1/eventos sin autenticación → 401")
    void listar_eventos_anonimo() throws Exception {
        mvc.perform(get("/api/v1/eventos")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    // ── Acceso autenticado con sesión ─────────────────────────────

    @Test @Order(8)
    @DisplayName("GET /api/v1/eventos con sesión válida → 200 paginado")
    void listar_eventos_autenticado() throws Exception {
        // Login para obtener cookie de sesión
        MvcResult loginResult = mvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(
                        new LoginRequest(CORREO_A, PASS))))
                .andExpect(status().isOk())
                .andReturn();

        String sessionCookie = loginResult.getResponse().getHeader("Set-Cookie");

        mvc.perform(get("/api/v1/eventos?page=0&size=10")
                .header("Cookie", sessionCookie)
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido").isArray())
                .andExpect(jsonPath("$.paginaActual").value(0))
                .andExpect(jsonPath("$.tamanioPagina").value(10))
                .andExpect(jsonPath("$.totalElementos").exists());
    }

    // ── Aislamiento entre usuarios ────────────────────────────────

    @Test @Order(9)
    @DisplayName("Aislamiento: usuario B no ve eventos de usuario A")
    void aislamiento_entre_usuarios() throws Exception {
        // Registrar usuario B
        mvc.perform(post("/auth/registro")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(
                        new RegistroRequest("Usuario B", CORREO_B, PASS))))
                .andExpect(status().isCreated());

        // Loguear A
        MvcResult loginA = mvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new LoginRequest(CORREO_A, PASS))))
                .andReturn();
        String cookieA = loginA.getResponse().getHeader("Set-Cookie");

        // Crear evento con usuario A
        String eventoJson = """
                {"modo":"PASIVO","distanciaCm":30.0}
                """;
        MvcResult postResult = mvc.perform(post("/api/v1/eventos")
                .contentType(MediaType.APPLICATION_JSON)
                .content(eventoJson)
                .header("Cookie", cookieA))
                .andExpect(status().isCreated())
                .andReturn();

        String eventoStr = postResult.getResponse().getContentAsString();
        Long idEvento = mapper.readTree(eventoStr).get("id").asLong();

        // Loguear B
        MvcResult loginB = mvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new LoginRequest(CORREO_B, PASS))))
                .andReturn();
        String cookieB = loginB.getResponse().getHeader("Set-Cookie");

        // Usuario B intenta acceder al evento de A → debe ser 404
        mvc.perform(get("/api/v1/eventos/" + idEvento)
                .header("Cookie", cookieB))
                .andExpect(status().isNotFound());

        // Lista de B debe estar vacía
        mvc.perform(get("/api/v1/eventos")
                .header("Cookie", cookieB)
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(0));
    }

    // ── Device Token ──────────────────────────────────────────────

    @Test @Order(10)
    @DisplayName("POST /api/v1/eventos con device token vacío → 401")
    void device_token_incorrecto() throws Exception {
        mvc.perform(post("/api/v1/eventos")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"modo\":\"PASIVO\",\"distanciaCm\":25.0}")
                .header("X-Device-Token", "token-incorrecto"))
                .andExpect(status().isUnauthorized());
    }

    // ── Validación de campos ──────────────────────────────────────

    @Test @Order(11)
    @DisplayName("POST /api/v1/eventos → 400 si modo inválido (con sesión)")
    void validacion_modo_invalido() throws Exception {
        MvcResult login = mvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new LoginRequest(CORREO_A, PASS))))
                .andReturn();
        String cookie = login.getResponse().getHeader("Set-Cookie");

        mvc.perform(post("/api/v1/eventos")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"modo\":\"INVALIDO\",\"distanciaCm\":30.0}")
                .header("Cookie", cookie))
                .andExpect(status().isBadRequest());
    }

    @Test @Order(12)
    @DisplayName("POST /api/v1/eventos → 400 si distanciaCm negativa (con sesión)")
    void validacion_distancia_negativa() throws Exception {
        MvcResult login = mvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new LoginRequest(CORREO_A, PASS))))
                .andReturn();
        String cookie = login.getResponse().getHeader("Set-Cookie");

        mvc.perform(post("/api/v1/eventos")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"modo\":\"PASIVO\",\"distanciaCm\":-5.0}")
                .header("Cookie", cookie))
                .andExpect(status().isBadRequest());
    }
}
