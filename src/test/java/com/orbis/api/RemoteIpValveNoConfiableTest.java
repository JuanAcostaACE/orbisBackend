package com.orbis.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbis.api.dto.LoginRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RemoteIpValve con un peer NO confiable.
 *
 * Todas las conexiones llegan desde 127.0.0.1. Para simular un par no confiable se restringe
 * internal-proxies a 10.x, así que el peer 127.0.0.1 no es de confianza y las cabeceras
 * X-Real-IP y X-Forwarded-For deben ignorarse. El contexto se reinicia por método porque
 * todos los intentos comparten la IP 127.0.0.1 y los contadores no deben arrastrarse.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:remoteip-no-confiable;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
                "server.tomcat.remoteip.internal-proxies=10[.][0-9]+[.][0-9]+[.][0-9]+",
                "app.ratelimit.login.max-intentos=2",
                "app.ratelimit.login.ventana-segundos=60"
        })
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class RemoteIpValveNoConfiableTest {

    @LocalServerPort int port;
    final ObjectMapper mapper = new ObjectMapper();

    private final HttpClient http = HttpClient.newHttpClient();

    private int login(String xRealIp, String xForwardedFor) throws Exception {
        String json = mapper.writeValueAsString(new LoginRequest("spoof@test.com", "contraseña_mala"));
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json));
        if (xRealIp != null) b.header("X-Real-IP", xRealIp);
        if (xForwardedFor != null) b.header("X-Forwarded-For", xForwardedFor);
        HttpResponse<String> r = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
        return r.statusCode();
    }

    @Test
    @DisplayName("Par no confiable: X-Real-IP se ignora; cambiarla no reinicia el límite")
    void x_real_ip_ignorada_desde_par_no_confiable() throws Exception {
        assertThat(login("203.0.113.10", null)).isEqualTo(401);
        assertThat(login("203.0.113.11", null)).isEqualTo(401);
        // Nueva X-Real-IP, mismo peer → sigue contando como el mismo cliente
        assertThat(login("203.0.113.12", null)).isEqualTo(429);
    }

    @Test
    @DisplayName("Par no confiable: X-Forwarded-For también se ignora")
    void x_forwarded_for_ignorada_desde_par_no_confiable() throws Exception {
        assertThat(login(null, "198.51.100.1")).isEqualTo(401);
        assertThat(login(null, "198.51.100.2")).isEqualTo(401);
        assertThat(login(null, "198.51.100.3")).isEqualTo(429);
    }
}
