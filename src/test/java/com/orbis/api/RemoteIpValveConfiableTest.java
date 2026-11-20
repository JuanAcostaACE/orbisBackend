package com.orbis.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbis.api.dto.LoginRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RemoteIpValve con el peer 127.0.0.1 (loopback, confiable por defecto).
 *
 * Usa un Tomcat real: MockMvc no ejecuta válvulas. Todas las conexiones llegan desde
 * 127.0.0.1, así que este peer es de confianza por la configuración por defecto.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:remoteip-confiable;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
                "app.ratelimit.login.max-intentos=2",
                "app.ratelimit.login.ventana-segundos=60"
        })
class RemoteIpValveConfiableTest {

    @LocalServerPort int port;
    final ObjectMapper mapper = new ObjectMapper();
    @Value("${server.tomcat.remoteip.internal-proxies}") String proxiesConfiados;

    private final HttpClient http = HttpClient.newHttpClient();

    private int login(String xRealIp, String xForwardedFor) throws Exception {
        String json = mapper.writeValueAsString(new LoginRequest("cliente@test.com", "contraseña_mala"));
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json));
        if (xRealIp != null) b.header("X-Real-IP", xRealIp);
        if (xForwardedFor != null) b.header("X-Forwarded-For", xForwardedFor);
        HttpResponse<String> r = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
        return r.statusCode();
    }

    @Test
    @DisplayName("Peer confiable: X-Real-IP identifica al cliente; cada IP tiene su propio límite")
    void x_real_ip_desde_proxy_confiable() throws Exception {
        assertThat(login("203.0.113.10", null)).isEqualTo(401);
        assertThat(login("203.0.113.10", null)).isEqualTo(401);
        assertThat(login("203.0.113.10", null)).isEqualTo(429);

        // Otro cliente detrás del mismo proxy: contador independiente
        assertThat(login("203.0.113.11", null)).isEqualTo(401);
    }

    @Test
    @DisplayName("Peer confiable: X-Forwarded-For se ignora aunque venga del proxy")
    void x_forwarded_for_ignorado() throws Exception {
        assertThat(login("203.0.113.20", "198.51.100.1")).isEqualTo(401);
        assertThat(login("203.0.113.20", "198.51.100.2")).isEqualTo(401);
        // Misma X-Real-IP, XFF distinto: sigue siendo el mismo cliente
        assertThat(login("203.0.113.20", "198.51.100.3")).isEqualTo(429);
    }

    @Test
    @DisplayName("Por defecto solo se confía en loopback y rangos privados, no en IP públicas")
    void patron_por_defecto_solo_privado() {
        Pattern p = Pattern.compile(proxiesConfiados);
        assertThat(p.matcher("127.0.0.1").matches()).isTrue();
        assertThat(p.matcher("10.1.2.3").matches()).isTrue();
        assertThat(p.matcher("192.168.1.5").matches()).isTrue();
        assertThat(p.matcher("172.20.0.1").matches()).isTrue();
        assertThat(p.matcher("fd12:3456::1").matches()).isTrue();
        assertThat(p.matcher("0:0:0:0:0:0:0:1").matches()).isTrue();

        assertThat(p.matcher("8.8.8.8").matches()).isFalse();
        assertThat(p.matcher("203.0.113.5").matches()).isFalse();
        assertThat(p.matcher("172.32.0.1").matches()).isFalse();
        assertThat(p.matcher("2001:db8::1").matches()).isFalse();
        assertThat(p.matcher("100.64.0.3").matches()).isFalse();
    }
}
