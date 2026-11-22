package com.orbis.api;

import com.orbis.api.model.UserAccount;
import com.orbis.api.repository.UserAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Los parámetros page/size de GET /api/v1/events se sanean en EventService:
 * size entre 1 y 100, page nunca negativa.
 */
@SpringBootTest
@AutoConfigureMockMvc
class EventsPaginationTest {

    static final String EMAIL_PATTERN = "paginacion@test.com";

    @Autowired MockMvc mvc;
    @Autowired UserAccountRepository userRepo;

    MockHttpSession userSession;

    @BeforeEach
    void userWithSession() {
        if (!userRepo.existsByEmail(EMAIL_PATTERN)) {
            UserAccount u = new UserAccount();
            u.setName("Paginacion");
            u.setEmail(EMAIL_PATTERN);
            u.setPasswordHash("{noop}sin-uso");   // no se hace login: la sesión se crea a mano
            userRepo.save(u);
        }
        // Mismo contexto que deja el login en la sesión (HttpSessionSecurityContextRepository)
        SecurityContext ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                EMAIL_PATTERN, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        userSession = new MockHttpSession();
        userSession.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, ctx);
    }

    @Test
    @DisplayName("Sin parámetros → página 0 de 20 elementos")
    void default_values() throws Exception {
        mvc.perform(get("/api/v1/events").session(userSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentPage").value(0))
                .andExpect(jsonPath("$.pageSize").value(20));
    }

    @Test
    @DisplayName("size mayor que 100 se limita a 100")
    void size_maximum() throws Exception {
        mvc.perform(get("/api/v1/events?size=5000").session(userSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pageSize").value(100));
    }

    @Test
    @DisplayName("size 0 o negativo se corrige a 1")
    void size_minimum() throws Exception {
        mvc.perform(get("/api/v1/events?size=0").session(userSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pageSize").value(1));
        mvc.perform(get("/api/v1/events?size=-7").session(userSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pageSize").value(1));
    }

    @Test
    @DisplayName("page negativa se corrige a 0")
    void negative_page() throws Exception {
        mvc.perform(get("/api/v1/events?page=-3").session(userSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentPage").value(0));
    }

    @Test
    @DisplayName("page no numérica → 400 en JSON")
    void non_numeric_page() throws Exception {
        mvc.perform(get("/api/v1/events?page=uno").session(userSession))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }
}
