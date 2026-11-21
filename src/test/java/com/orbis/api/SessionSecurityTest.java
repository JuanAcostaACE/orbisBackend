package com.orbis.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbis.api.dto.LoginRequest;
import com.orbis.api.dto.RegisterRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Sesión, logout, /auth/me, endpoints públicos/protegidos y session fixation.
 * BD H2 propia para no compartir datos con otras clases de prueba.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "spring.datasource.url=jdbc:h2:mem:seguridad-sesion;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@AutoConfigureMockMvc
class SessionSecurityTest {

    @Autowired MockMvc mvc;
    final ObjectMapper mapper = new ObjectMapper();

    static final String EMAIL_PATTERN = "sesion@test.com";
    static final String PASS   = "contraseña_segura_123";

    private void register(String email) throws Exception {
        mvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new RegisterRequest("Sesion Test", email, PASS))))
                .andExpect(status().isCreated());
    }

    private MockHttpSession login(String email) throws Exception {
        MvcResult r = mvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new LoginRequest(email, PASS))))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) r.getRequest().getSession(false);
    }

    @Test
    @DisplayName("GET /health es público y responde UP")
    void health_public() throws Exception {
        mvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("Endpoint protegido sin sesión → 401 JSON")
    void protected_without_session_401() throws Exception {
        mvc.perform(get("/api/v1/events").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("Consola H2 desactivada en perfil por defecto: /h2-console no es accesible")
    void h2_console_disabled() throws Exception {
        mvc.perform(get("/h2-console")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /auth/me sin sesión → 401")
    void me_without_session_401() throws Exception {
        mvc.perform(get("/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /auth/me con sesión → 200 con el correo del usuario")
    void me_with_session_200() throws Exception {
        String email = "me@test.com";
        register(email);
        MockHttpSession userSession = login(email);

        mvc.perform(get("/auth/me").session(userSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));
    }

    @Test
    @DisplayName("POST /auth/logout invalida la sesión: /auth/me vuelve a 401")
    void logout_invalidates_session() throws Exception {
        String email = "logout@test.com";
        register(email);
        MockHttpSession userSession = login(email);

        mvc.perform(get("/auth/me").session(userSession)).andExpect(status().isOk());

        mvc.perform(post("/auth/logout").session(userSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());

        mvc.perform(get("/auth/me").session(userSession))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /auth/logout emite Set-Cookie ORBIS_SID con Max-Age=0 para borrar la cookie del navegador")
    void logout_expires_cookie_in_browser() throws Exception {
        String email = "logoutcookie@test.com";
        register(email);
        MockHttpSession userSession = login(email);

        mvc.perform(post("/auth/logout").session(userSession))
                .andExpect(status().isOk())
                .andExpect(header().string("Set-Cookie", containsString("ORBIS_SID=;")))
                .andExpect(header().string("Set-Cookie", containsString("Max-Age=0")))
                .andExpect(header().string("Set-Cookie", containsString("Path=/")))
                .andExpect(header().string("Set-Cookie", containsString("HttpOnly")))
                .andExpect(header().string("Set-Cookie", containsString("SameSite=Strict")));
    }

    @Test
    @DisplayName("Login cambia el ID de una sesión preexistente (session fixation)")
    void login_regenerates_session_id() throws Exception {
        String email = "fixation@test.com";
        register(email);

        MockHttpSession preLogin = new MockHttpSession();
        preLogin.setAttribute("previo", "valor");
        String idBefore = preLogin.getId();

        MvcResult r = mvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new LoginRequest(email, PASS)))
                .session(preLogin))
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession postLogin = (MockHttpSession) r.getRequest().getSession(false);
        assertThat(postLogin).isNotNull();
        assertThat(postLogin.getId()).isNotEqualTo(idBefore);
    }

    @Test
    @DisplayName("Duplicado en registro: 201 con el mismo cuerpo que un correo nuevo, sin revelar el correo")
    void register_duplicate_generic_message() throws Exception {
        String email = "dup@test.com";
        String newBody = mvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new RegisterRequest("Nuevo", email, PASS))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        MvcResult r = mvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new RegisterRequest("Otro", email, PASS))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(201))
                .andReturn();

        String responseBody = r.getResponse().getContentAsString();
        assertThat(responseBody).isEqualTo(newBody);
        assertThat(responseBody).doesNotContain("ya está registrado");
        assertThat(responseBody).doesNotContain(email);
    }
}
