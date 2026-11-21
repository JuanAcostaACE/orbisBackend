package com.orbis.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbis.api.dto.RegisterRequest;
import com.orbis.api.model.UserAccount;
import com.orbis.api.repository.UserAccountRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Registro anti-enumeración: el mismo status y el mismo cuerpo exista o no el correo,
 * una sola fila por correo y 400 para datos inválidos.
 * BD H2 propia y límites de rate limit altos para no interferir con otras pruebas.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:registro-anonimato;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
                "app.ratelimit.register.max-attempts=1000"
        })
@AutoConfigureMockMvc
class RegisterAnonymityTest {

    static final String EXPECTED =
            "{\"status\":201,\"message\":\"Solicitud procesada. Inicia sesión para continuar.\"}";
    static final String PASS = "contraseña_segura_123";

    @Autowired MockMvc mvc;
    @Autowired UserAccountRepository userRepo;
    final ObjectMapper mapper = new ObjectMapper();

    private MvcResult register(String name, String email, String password) throws Exception {
        return mvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new RegisterRequest(name, email, password))))
                .andReturn();
    }

    private long rowsFor(String email) {
        List<UserAccount> all = userRepo.findAll();
        return all.stream().filter(u -> u.getEmail().equals(email)).count();
    }

    @Test
    @DisplayName("Correo nuevo y correo ya registrado: mismo status 201 y mismo cuerpo exacto")
    void same_status_and_body_new_and_duplicate() throws Exception {
        String email = "nuevo-anon@test.com";

        MvcResult fresh = register("Nuevo", email, PASS);
        MvcResult duplicate = register("Otro nombre", email, PASS);

        assertThat(fresh.getResponse().getStatus()).isEqualTo(201);
        assertThat(duplicate.getResponse().getStatus()).isEqualTo(201);
        assertThat(fresh.getResponse().getContentAsString()).isEqualTo(EXPECTED);
        assertThat(duplicate.getResponse().getContentAsString()).isEqualTo(EXPECTED);
    }

    @Test
    @DisplayName("Registro duplicado no crea una segunda fila para el correo")
    void no_duplicate_row() throws Exception {
        String email = "unica-fila@test.com";
        register("Primero", email, PASS);
        register("Segundo", email, PASS);
        register("Tercero", email, PASS);

        assertThat(rowsFor(email)).isEqualTo(1);
    }

    @Test
    @DisplayName("El correo se normaliza: mayúsculas y duplicado comparten fila y respuesta")
    void normalized_email() throws Exception {
        MvcResult a = register("Mayus", "MAYUS-anon@Test.com", PASS);
        MvcResult b = register("Minus", "mayus-anon@test.com", PASS);

        assertThat(a.getResponse().getContentAsString()).isEqualTo(EXPECTED);
        assertThat(b.getResponse().getContentAsString()).isEqualTo(EXPECTED);
        assertThat(rowsFor("mayus-anon@test.com")).isEqualTo(1);
    }

    @Test
    @DisplayName("Datos inválidos siguen devolviendo 400, también con un correo ya registrado")
    void invalid_400_even_if_exists() throws Exception {
        String existing = "invalido-anon@test.com";
        register("Existe", existing, PASS);

        mvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new RegisterRequest("X", "no-es-correo", PASS))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        mvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new RegisterRequest("X", existing, "123"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }
}
