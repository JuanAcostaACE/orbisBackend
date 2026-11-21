package com.orbis.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbis.api.dto.LoginRequest;
import com.orbis.api.dto.RegisterRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Límite de intentos fallidos POR CUENTA (5 por ventana de 15 min) combinado con el límite por IP.
 *
 * - Una IP puede bloquear una cuenta sin bloquear a otras cuentas de esa misma IP.
 * - El bloqueo de una cuenta aplica desde cualquier IP.
 * - Cuenta bloqueada y cuenta inexistente responden igual (429, mismo cuerpo).
 * - Un login correcto borra el contador.
 *
 * Límite por IP alto para que no interfiera. Cada prueba usa correos propios porque el
 * contexto (y los contadores) se comparte entre métodos.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:bloqueo-cuenta;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
                "app.ratelimit.login.max-attempts=1000",
                "app.ratelimit.account.max-attempts=5",
                "app.ratelimit.account.window-seconds=900"
        })
@AutoConfigureMockMvc
class AccountLockoutTest {

    static final String PASS = "contraseña_segura_123";

    @Autowired MockMvc mvc;
    final ObjectMapper mapper = new ObjectMapper();

    private void register(String email) throws Exception {
        mvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new RegisterRequest("Usuario", email, PASS))))
                .andExpect(status().isCreated());
    }

    private MockHttpServletRequestBuilder login(String ip, String email, String pass) throws Exception {
        return post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new LoginRequest(email, pass)))
                .with(req -> { req.setRemoteAddr(ip); return req; });
    }

    private void failures(String ip, String email, int repetitions) throws Exception {
        for (int i = 0; i < repetitions; i++) {
            mvc.perform(login(ip, email, "contraseña_mala"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    @DisplayName("Una IP bloquea una cuenta; otra cuenta de la misma IP sigue entrando")
    void same_ip_locks_one_account_not_others() throws Exception {
        String victim = "victima-ip@test.com";
        String other = "otra-ip@test.com";
        register(victim);
        register(other);

        failures("198.51.100.7", victim, 5);
        mvc.perform(login("198.51.100.7", victim, PASS))
                .andExpect(status().isTooManyRequests());

        mvc.perform(login("198.51.100.7", other, PASS))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("El bloqueo de una cuenta aplica desde otra IP")
    void lockout_applies_across_ips() throws Exception {
        String email = "entre-ips@test.com";
        register(email);

        failures("198.51.100.20", email, 5);

        mvc.perform(login("198.51.100.21", email, PASS))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    @DisplayName("Cuenta bloqueada y cuenta inexistente responden igual (status, mensaje y forma)")
    void locked_and_nonexistent_same() throws Exception {
        String existing = "existe-bloq@test.com";
        String nonexistent = "no-existe-bloq@test.com";
        register(existing);

        failures("198.51.100.30", existing, 5);
        failures("198.51.100.30", nonexistent, 5);

        MvcResult rExisting = mvc.perform(login("198.51.100.31", existing, PASS))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andReturn();
        MvcResult rNonexistent = mvc.perform(login("198.51.100.31", nonexistent, PASS))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andReturn();

        JsonNode a = mapper.readTree(rExisting.getResponse().getContentAsString());
        JsonNode b = mapper.readTree(rNonexistent.getResponse().getContentAsString());
        assertThat(a.get("status").asInt()).isEqualTo(429);
        assertThat(b.get("status").asInt()).isEqualTo(a.get("status").asInt());
        assertThat(b.get("message").asText()).isEqualTo(a.get("message").asText());
        assertThat(a.size()).isEqualTo(b.size());
        for (String field : new String[]{"status", "message", "timestamp"}) {
            assertThat(a.has(field)).isTrue();
            assertThat(b.has(field)).isTrue();
        }
    }

    @Test
    @DisplayName("Un login correcto borra el contador de la cuenta")
    void successful_login_clears_counter() throws Exception {
        String email = "reset-cuenta@test.com";
        register(email);

        failures("198.51.100.40", email, 4);
        mvc.perform(login("198.51.100.40", email, PASS)).andExpect(status().isOk());

        // Tras el reset, 4 fallos más no bloquean (el quinto aún responde 401)
        failures("198.51.100.40", email, 5);
        mvc.perform(login("198.51.100.40", email, PASS))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("El correo se normaliza: mayúsculas y minúsculas comparten contador")
    void normalized_email_shares_counter() throws Exception {
        register("mixto@test.com");

        failures("198.51.100.50", "MIXTO@test.com", 3);
        failures("198.51.100.50", "Mixto@Test.com", 2);

        mvc.perform(login("198.51.100.50", "mixto@test.com", PASS))
                .andExpect(status().isTooManyRequests());
    }
}
