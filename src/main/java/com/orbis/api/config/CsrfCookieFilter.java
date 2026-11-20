package com.orbis.api.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Fuerza la escritura de la cookie XSRF-TOKEN en cada petición.
 *
 * Spring solo emite la cookie cuando alguien accede al token. Una API sin plantillas nunca lo hace,
 * así que el SPA no recibía la cookie y no podía enviar la cabecera (403 en cada POST con sesión).
 * Patrón recomendado por la documentación de Spring Security para aplicaciones SPA.
 */
public class CsrfCookieFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (token != null) {
            token.getToken(); // accede al token para que CookieCsrfTokenRepository escriba la cookie
        }
        filterChain.doFilter(request, response);
    }
}
