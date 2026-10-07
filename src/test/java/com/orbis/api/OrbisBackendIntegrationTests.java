package com.orbis.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class OrbisBackendIntegrationTests {

    @Autowired
    private ApplicationContext context;

    @Test
    @DisplayName("Contexto de Spring carga correctamente")
    void contextLoads() {
        assertThat(context).isNotNull();
    }

    @Test
    @DisplayName("EventoService esta en el contexto")
    void eventoServiceExists() {
        assertThat(context.containsBean("eventoService")).isTrue();
    }

    @Test
    @DisplayName("StrategyFactory esta en el contexto")
    void strategyFactoryExists() {
        assertThat(context.containsBean("strategyFactory")).isTrue();
    }
}