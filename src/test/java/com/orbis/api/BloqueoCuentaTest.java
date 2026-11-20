package com.orbis.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbis.api.dto.LoginRequest;
import com.orbis.api.dto.RegistroRequest;
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
                "app.ratelimit.login.max-intentos=1000",
                "app.ratelimit.cuenta.max-intentos=5",
                "app.ratelimit.cuenta.ventana-segundos=900"
        })
@AutoConfigureMockMvc
class BloqueoCuentaTest {

    static final String PASS = "contraseña_segura_123";

    @Autowired MockMvc mvc;
    final ObjectMapper mapper = new ObjectMapper();

    private void registrar(String correo) throws Exception {
        mvc.perform(post("/auth/registro")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new RegistroRequest("Usuario", correo, PASS))))
                .andExpect(status().isCreated());
    }

    private MockHttpServletRequestBuilder login(String ip, String correo, String pass) throws Exception {
        return post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new LoginRequest(correo, pass)))
                .with(req -> { req.setRemoteAddr(ip); return req; });
    }

    private void fallos(String ip, String correo, int veces) throws Exception {
        for (int i = 0; i < veces; i++) {
            mvc.perform(login(ip, correo, "contraseña_mala"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    @DisplayName("Una IP bloquea una cuenta; otra cuenta de la misma IP sigue entrando")
    void misma_ip_bloquea_una_cuenta_no_las_demas() throws Exception {
        String victima = "victima-ip@test.com";
        String otra = "otra-ip@test.com";
        registrar(victima);
        registrar(otra);

        fallos("198.51.100.7", victima, 5);
        mvc.perform(login("198.51.100.7", victima, PASS))
                .andExpect(status().isTooManyRequests());

        mvc.perform(login("198.51.100.7", otra, PASS))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("El bloqueo de una cuenta aplica desde otra IP")
    void bloqueo_aplica_entre_ips() throws Exception {
        String correo = "entre-ips@test.com";
        registrar(correo);

        fallos("198.51.100.20", correo, 5);

        mvc.perform(login("198.51.100.21", correo, PASS))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    @DisplayName("Cuenta bloqueada y cuenta inexistente responden igual (status, mensaje y forma)")
    void bloqueada_y_inexistente_igual() throws Exception {
        String existente = "existe-bloq@test.com";
        String inexistente = "no-existe-bloq@test.com";
        registrar(existente);

        fallos("198.51.100.30", existente, 5);
        fallos("198.51.100.30", inexistente, 5);

        MvcResult rExistente = mvc.perform(login("198.51.100.31", existente, PASS))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andReturn();
        MvcResult rInexistente = mvc.perform(login("198.51.100.31", inexistente, PASS))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andReturn();

        JsonNode a = mapper.readTree(rExistente.getResponse().getContentAsString());
        JsonNode b = mapper.readTree(rInexistente.getResponse().getContentAsString());
        assertThat(a.get("status").asInt()).isEqualTo(429);
        assertThat(b.get("status").asInt()).isEqualTo(a.get("status").asInt());
        assertThat(b.get("mensaje").asText()).isEqualTo(a.get("mensaje").asText());
        assertThat(a.size()).isEqualTo(b.size());
        for (String campo : new String[]{"status", "mensaje", "timestamp"}) {
            assertThat(a.has(campo)).isTrue();
            assertThat(b.has(campo)).isTrue();
        }
    }

    @Test
    @DisplayName("Un login correcto borra el contador de la cuenta")
    void login_correcto_borra_contador() throws Exception {
        String correo = "reset-cuenta@test.com";
        registrar(correo);

        fallos("198.51.100.40", correo, 4);
        mvc.perform(login("198.51.100.40", correo, PASS)).andExpect(status().isOk());

        // Tras el reset, 4 fallos más no bloquean (el quinto aún responde 401)
        fallos("198.51.100.40", correo, 5);
        mvc.perform(login("198.51.100.40", correo, PASS))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("El correo se normaliza: mayúsculas y minúsculas comparten contador")
    void correo_normalizado_comparte_contador() throws Exception {
        registrar("mixto@test.com");

        fallos("198.51.100.50", "MIXTO@test.com", 3);
        fallos("198.51.100.50", "Mixto@Test.com", 2);

        mvc.perform(login("198.51.100.50", "mixto@test.com", PASS))
                .andExpect(status().isTooManyRequests());
    }
}
