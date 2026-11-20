package com.orbis.api;

import com.orbis.api.model.Usuario;
import com.orbis.api.repository.UsuarioRepository;
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
 * Los parámetros page/size de GET /api/v1/eventos se sanean en EventoService:
 * size entre 1 y 100, page nunca negativa.
 */
@SpringBootTest
@AutoConfigureMockMvc
class EventosPaginacionTest {

    static final String CORREO = "paginacion@test.com";

    @Autowired MockMvc mvc;
    @Autowired UsuarioRepository usuarioRepo;

    MockHttpSession sesion;

    @BeforeEach
    void usuarioConSesion() {
        if (!usuarioRepo.existsByCorreo(CORREO)) {
            Usuario u = new Usuario();
            u.setNombre("Paginacion");
            u.setCorreo(CORREO);
            u.setHashContrasena("{noop}sin-uso");   // no se hace login: la sesión se crea a mano
            usuarioRepo.save(u);
        }
        // Mismo contexto que deja el login en la sesión (HttpSessionSecurityContextRepository)
        SecurityContext ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                CORREO, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        sesion = new MockHttpSession();
        sesion.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, ctx);
    }

    @Test
    @DisplayName("Sin parámetros → página 0 de 20 elementos")
    void valores_por_defecto() throws Exception {
        mvc.perform(get("/api/v1/eventos").session(sesion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paginaActual").value(0))
                .andExpect(jsonPath("$.tamanioPagina").value(20));
    }

    @Test
    @DisplayName("size mayor que 100 se limita a 100")
    void size_maximo() throws Exception {
        mvc.perform(get("/api/v1/eventos?size=5000").session(sesion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tamanioPagina").value(100));
    }

    @Test
    @DisplayName("size 0 o negativo se corrige a 1")
    void size_minimo() throws Exception {
        mvc.perform(get("/api/v1/eventos?size=0").session(sesion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tamanioPagina").value(1));
        mvc.perform(get("/api/v1/eventos?size=-7").session(sesion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tamanioPagina").value(1));
    }
}
