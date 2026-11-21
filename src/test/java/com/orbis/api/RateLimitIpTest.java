package com.orbis.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbis.api.dto.LoginRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Límite por IP: el contador es por cliente y no global, y el filtro no cambia de identidad
 * por cabeceras.
 *
 * MockMvc no ejecuta válvulas de Tomcat, así que aquí solo se prueba RateLimitFilter con
 * request.setRemoteAddr(...). La resolución de X-Real-IP (RemoteIpValve) se prueba con un
 * Tomcat real en RemoteIpValveTrustedTest y RemoteIpValveUntrustedTest.
 * Login: 2 intentos por ventana.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:rate-limit-ip;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
                "app.ratelimit.login.max-attempts=2",
                "app.ratelimit.login.window-seconds=60"
        })
@AutoConfigureMockMvc
class RateLimitIpTest {

    @Autowired MockMvc mvc;
    final ObjectMapper mapper = new ObjectMapper();

    private MockHttpServletRequestBuilder loginFrom(String ip, String email) throws Exception {
        return post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new LoginRequest(email, "contraseña_mala")))
                .with(req -> { req.setRemoteAddr(ip); return req; });
    }

    @Test
    @DisplayName("El límite es por IP: una IP bloqueada no afecta a otra IP")
    void limit_per_ip_not_global() throws Exception {
        for (int i = 0; i < 2; i++) {
            mvc.perform(loginFrom("198.51.100.1", "a-ip@test.com"))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(loginFrom("198.51.100.1", "a-ip@test.com"))
                .andExpect(status().isTooManyRequests());

        // Otra IP sigue con su propio contador
        mvc.perform(loginFrom("198.51.100.2", "a-ip@test.com"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Un cliente de IP no confiable no cambia su identidad enviando X-Real-IP o X-Forwarded-For")
    void headers_do_not_change_identity_from_untrusted_peer() throws Exception {
        String peerIp = "203.0.113.50";
        for (int i = 0; i < 2; i++) {
            mvc.perform(loginFrom(peerIp, "spoof@test.com")
                            .header("X-Real-IP", "192.0.2." + (10 + i))
                            .header("X-Forwarded-For", "192.0.2.99"))
                    .andExpect(status().isUnauthorized());
        }

        // Cabeceras nuevas en cada petición: sigue siendo la misma IP de conexión → 429
        mvc.perform(loginFrom(peerIp, "spoof@test.com")
                        .header("X-Real-IP", "192.0.2.200")
                        .header("X-Forwarded-For", "192.0.2.201"))
                .andExpect(status().isTooManyRequests());
    }
}
