package com.orbis.api.config;

import com.orbis.api.model.SesionMovil;
import com.orbis.api.service.SesionMovilService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Autenticación de la app móvil con token de acceso (Authorization: Bearer).
 *
 * Solo actúa si el contexto está vacío. Si el token es una sesión móvil válida (existe,
 * no revocada, no caducada), autentica al correo con ROLE_USER y deja el id de la sesión en
 * el atributo de request {@link #ATRIBUTO_SESION_MOVIL_ID}. Si no es válido, no autentica y
 * no registra nada: un token de dispositivo cae aquí sin efecto y lo gestiona DispositivoAuthFilter.
 */
@Component
public class SesionMovilAuthFilter extends OncePerRequestFilter {

    public static final String ATRIBUTO_SESION_MOVIL_ID = "sesionMovilId";

    private static final String BEARER = "Bearer ";

    private final SesionMovilService sesionMovilService;

    public SesionMovilAuthFilter(SesionMovilService sesionMovilService) {
        this.sesionMovilService = sesionMovilService;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        String cabecera = request.getHeader("Authorization");
        if (SecurityContextHolder.getContext().getAuthentication() == null
                && cabecera != null && cabecera.startsWith(BEARER)) {
            Optional<SesionMovil> sesion = sesionMovilService.autenticar(cabecera.substring(BEARER.length()).trim());
            sesion.ifPresent(s -> {
                SecurityContext ctx = SecurityContextHolder.createEmptyContext();
                ctx.setAuthentication(new UsernamePasswordAuthenticationToken(
                        s.getCorreo(),
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))));
                SecurityContextHolder.setContext(ctx);
                request.setAttribute(ATRIBUTO_SESION_MOVIL_ID, s.getId());
            });
        }
        filterChain.doFilter(request, response);
    }
}
