package com.orbis.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prueba de integración de arranque del contexto.
 * Verifica que los beans principales existen y Spring Security está activo.
 *
 * Nota: WebEnvironment.NONE para evitar levantar el servidor HTTP completo.
 * Las pruebas de endpoints HTTP están en EventoControllerTest y AuthControllerTest.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class OrbisBackendIntegrationTests {

    @Autowired
    private ApplicationContext context;

    @Test
    @DisplayName("El contexto de Spring carga correctamente con Security")
    void contextLoads() {
        assertThat(context).isNotNull();
    }

    @Test
    @DisplayName("EventoService está en el contexto")
    void eventoServiceExists() {
        assertThat(context.containsBean("eventoService")).isTrue();
    }

    @Test
    @DisplayName("StrategyFactory está en el contexto")
    void strategyFactoryExists() {
        assertThat(context.containsBean("strategyFactory")).isTrue();
    }

    @Test
    @DisplayName("AuthService está en el contexto")
    void authServiceExists() {
        assertThat(context.containsBean("authService")).isTrue();
    }

    @Test
    @DisplayName("SecurityFilterChain está configurado (bean 'filterChain')")
    void securityFilterChainExists() {
        assertThat(context.containsBean("filterChain")
                || context.containsBean("securityFilterChain")
                || context.getBeansOfType(
                    org.springframework.security.web.SecurityFilterChain.class
                ).size() > 0
        ).isTrue();
    }

    @Test
    @DisplayName("PasswordEncoder BCrypt está en el contexto")
    void passwordEncoderExists() {
        assertThat(context.containsBean("passwordEncoder")).isTrue();
    }
}