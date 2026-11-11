package com.orbis.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbis.api.dto.LoginRequest;
import com.orbis.api.dto.RegistroRequest;
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
class SeguridadSesionTest {

    @Autowired MockMvc mvc;
    final ObjectMapper mapper = new ObjectMapper();

    static final String CORREO = "sesion@test.com";
    static final String PASS   = "contraseña_segura_123";

    private void registrar(String correo) throws Exception {
        mvc.perform(post("/auth/registro")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new RegistroRequest("Sesion Test", correo, PASS))))
                .andExpect(status().isCreated());
    }

    private MockHttpSession login(String correo) throws Exception {
        MvcResult r = mvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new LoginRequest(correo, PASS))))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) r.getRequest().getSession(false);
    }

    @Test
    @DisplayName("GET /health es público y responde UP")
    void health_publico() throws Exception {
        mvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("Endpoint protegido sin sesión → 401 JSON")
    void protegido_sin_sesion_401() throws Exception {
        mvc.perform(get("/api/v1/eventos").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("Consola H2 desactivada en perfil por defecto: /h2-console no es accesible")
    void h2_console_desactivada() throws Exception {
        mvc.perform(get("/h2-console")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /auth/me sin sesión → 401")
    void me_sin_sesion_401() throws Exception {
        mvc.perform(get("/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /auth/me con sesión → 200 con el correo del usuario")
    void me_con_sesion_200() throws Exception {
        String correo = "me@test.com";
        registrar(correo);
        MockHttpSession sesion = login(correo);

        mvc.perform(get("/auth/me").session(sesion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correo").value(correo));
    }

    @Test
    @DisplayName("POST /auth/logout invalida la sesión: /auth/me vuelve a 401")
    void logout_invalida_sesion() throws Exception {
        String correo = "logout@test.com";
        registrar(correo);
        MockHttpSession sesion = login(correo);

        mvc.perform(get("/auth/me").session(sesion)).andExpect(status().isOk());

        mvc.perform(post("/auth/logout").session(sesion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").exists());

        mvc.perform(get("/auth/me").session(sesion))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Login cambia el ID de una sesión preexistente (session fixation)")
    void login_regenera_id_de_sesion() throws Exception {
        String correo = "fixation@test.com";
        registrar(correo);

        MockHttpSession preLogin = new MockHttpSession();
        preLogin.setAttribute("previo", "valor");
        String idAntes = preLogin.getId();

        MvcResult r = mvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new LoginRequest(correo, PASS)))
                .session(preLogin))
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession postLogin = (MockHttpSession) r.getRequest().getSession(false);
        assertThat(postLogin).isNotNull();
        assertThat(postLogin.getId()).isNotEqualTo(idAntes);
    }

    @Test
    @DisplayName("Duplicado en registro: 400 con mensaje que no revela la existencia del correo")
    void registro_duplicado_mensaje_generico() throws Exception {
        String correo = "dup@test.com";
        registrar(correo);

        MvcResult r = mvc.perform(post("/auth/registro")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new RegistroRequest("Otro", correo, PASS))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.mensaje").exists())
                .andReturn();

        String cuerpo = r.getResponse().getContentAsString();
        assertThat(cuerpo).doesNotContain("ya está registrado");
        assertThat(cuerpo).doesNotContain(correo);
    }
}
