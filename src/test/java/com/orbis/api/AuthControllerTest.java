package com.orbis.api;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.orbis.api.dto.LoginRequest;
import com.orbis.api.dto.RegisterRequest;
import com.orbis.api.model.UserAccount;
import com.orbis.api.repository.UserAccountRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests de integración para autenticación y autorización.
 *
 * Verifica:
 *  - Registro: 201 uniforme (nuevo o duplicado), campos inválidos 400.
 *  - Login: credenciales correctas 200, incorrectas 401.
 *  - Acceso anónimo a /api/v1/events → 401.
 *  - Acceso con sesión a /api/v1/events → 200 (paginado).
 *  - Aislamiento: usuario A no ve eventos de usuario B.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuthControllerTest {

    @Autowired MockMvc         mvc;
    final ObjectMapper         mapper = new ObjectMapper();
    @Autowired UserAccountRepository userRepo;
    @Autowired PasswordEncoder passwordEncoder;

    static final String EMAIL_A = "usuarioa@test.com";
    static final String EMAIL_B = "usuariob@test.com";
    static final String PASS     = "contraseña_segura_123";

    // ── Registro ──────────────────────────────────────────────────

    @Test @Order(1)
    @DisplayName("POST /auth/register → 201 con cuerpo uniforme (correo nuevo)")
    void register_valid() throws Exception {
        mvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(
                        new RegisterRequest("Usuario A", EMAIL_A, PASS))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(201))
                .andExpect(jsonPath("$.message").value(
                        "Solicitud procesada. Inicia sesión para continuar."))
                .andExpect(jsonPath("$.email").doesNotExist());
    }

    @Test @Order(2)
    @DisplayName("POST /auth/register → 201 con el mismo cuerpo si correo ya existe (anti-enumeración)")
    void register_duplicate() throws Exception {
        // El usuario A ya existe del test anterior
        mvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(
                        new RegisterRequest("Duplicado", EMAIL_A, PASS))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(201))
                .andExpect(jsonPath("$.message").value(
                        "Solicitud procesada. Inicia sesión para continuar."));
    }

    @Test @Order(3)
    @DisplayName("POST /auth/register → 400 si contraseña < 8 chars")
    void register_short_password() throws Exception {
        mvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(
                        new RegisterRequest("Test", "nuevo@test.com", "123"))))
                .andExpect(status().isBadRequest());
    }

    @Test @Order(4)
    @DisplayName("POST /auth/register → 400 si correo inválido")
    void register_invalid_email() throws Exception {
        mvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(
                        new RegisterRequest("Test", "no-es-correo", PASS))))
                .andExpect(status().isBadRequest());
    }

    // ── Login ─────────────────────────────────────────────────────

    @Test @Order(5)
    @DisplayName("POST /auth/login → 200 con credenciales correctas")
    void login_valid() throws Exception {
        mvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(
                        new LoginRequest(EMAIL_A, PASS))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(EMAIL_A));
    }

    @Test @Order(6)
    @DisplayName("POST /auth/login → 401 con contraseña incorrecta")
    void login_wrong_credentials() throws Exception {
        mvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(
                        new LoginRequest(EMAIL_A, "contraseña_mala"))))
                .andExpect(status().isUnauthorized());
    }

    // ── Acceso anónimo ────────────────────────────────────────────

    @Test @Order(7)
    @DisplayName("GET /api/v1/events sin autenticación → 401")
    void list_events_anonymous() throws Exception {
        mvc.perform(get("/api/v1/events")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    // ── Acceso autenticado con sesión ─────────────────────────────

    @Test @Order(8)
    @DisplayName("GET /api/v1/events con sesión válida → 200 paginado")
    void list_events_authenticated() throws Exception {
        // Login para obtener cookie de sesión
        MvcResult loginResult = mvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(
                        new LoginRequest(EMAIL_A, PASS))))
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession userSession = (MockHttpSession) loginResult.getRequest().getSession(false);

        mvc.perform(get("/api/v1/events?page=0&size=10")
                .session(userSession)
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.currentPage").value(0))
                .andExpect(jsonPath("$.pageSize").value(10))
                .andExpect(jsonPath("$.totalElements").exists());
    }

    // ── Aislamiento entre usuarios ────────────────────────────────

    @Test @Order(9)
    @DisplayName("Aislamiento: usuario B no ve eventos de usuario A")
    void isolation_between_users() throws Exception {
        // Registrar usuario B
        mvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(
                        new RegisterRequest("Usuario B", EMAIL_B, PASS))))
                .andExpect(status().isCreated());

        // Loguear A
        MvcResult loginA = mvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new LoginRequest(EMAIL_A, PASS))))
                .andReturn();
        MockHttpSession cookieA = (MockHttpSession) loginA.getRequest().getSession(false);

        // Crear evento con usuario A
        String eventJson = """
                {"mode":"PASSIVE","distanceCm":30.0}
                """;
        MvcResult postResult = mvc.perform(post("/api/v1/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(eventJson)
                .session(cookieA))
                .andExpect(status().isCreated())
                .andReturn();

        String eventStr = postResult.getResponse().getContentAsString();
        Long eventId = mapper.readTree(eventStr).get("id").asLong();

        // Loguear B
        MvcResult loginB = mvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new LoginRequest(EMAIL_B, PASS))))
                .andReturn();
        MockHttpSession cookieB = (MockHttpSession) loginB.getRequest().getSession(false);

        // Usuario B intenta acceder al evento de A → debe ser 404
        mvc.perform(get("/api/v1/events/" + eventId)
                .session(cookieB))
                .andExpect(status().isNotFound());

        // Lista de B debe estar vacía
        mvc.perform(get("/api/v1/events")
                .session(cookieB)
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    // ── Device Token ──────────────────────────────────────────────

    @Test @Order(10)
    @DisplayName("POST /api/v1/events con device token vacío → 401")
    void device_token_incorrect() throws Exception {
        mvc.perform(post("/api/v1/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"mode\":\"PASSIVE\",\"distanceCm\":25.0}")
                .header("X-Device-Token", "token-incorrecto"))
                .andExpect(status().isUnauthorized());
    }

    // ── Validación de campos ──────────────────────────────────────

    @Test @Order(11)
    @DisplayName("POST /api/v1/events → 400 si modo inválido (con sesión)")
    void validation_invalid_mode() throws Exception {
        MvcResult login = mvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new LoginRequest(EMAIL_A, PASS))))
                .andReturn();
        MockHttpSession cookie = (MockHttpSession) login.getRequest().getSession(false);

        mvc.perform(post("/api/v1/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"mode\":\"INVALID\",\"distanceCm\":30.0}")
                .session(cookie))
                .andExpect(status().isBadRequest());
    }

    @Test @Order(12)
    @DisplayName("POST /api/v1/events → 400 si distanceCm negativa (con sesión)")
    void validation_negative_distance() throws Exception {
        MvcResult login = mvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new LoginRequest(EMAIL_A, PASS))))
                .andReturn();
        MockHttpSession cookie = (MockHttpSession) login.getRequest().getSession(false);

        mvc.perform(post("/api/v1/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"mode\":\"PASSIVE\",\"distanceCm\":-5.0}")
                .session(cookie))
                .andExpect(status().isBadRequest());
    }
}
