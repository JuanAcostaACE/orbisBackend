package com.orbis.api.config;

import com.orbis.api.model.Dispositivo;
import com.orbis.api.service.DispositivoService;
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
 * Autenticación de bastones con token propio por dispositivo (D-002).
 *
 * Cabecera: Authorization: Bearer &lt;token&gt;. Si el token es válido, el principal es
 * "device:&lt;deviceId&gt;" con ROLE_DEVICE, y el propietario queda en el atributo
 * "deviceOwnerEmail" (el mismo atributo que usa el token compartido legado).
 *
 * Si el token no es válido, el filtro no autentica: la petición termina en 401.
 */
@Component
public class DispositivoAuthFilter extends OncePerRequestFilter {

    public static final String PREFIJO_PRINCIPAL = "device:";
    public static final String ATRIBUTO_DEVICE_ID = "deviceId";
    public static final String ATRIBUTO_PROPIETARIO = "deviceOwnerEmail";

    private static final String BEARER = "Bearer ";

    private final DispositivoService dispositivoService;

    public DispositivoAuthFilter(DispositivoService dispositivoService) {
        this.dispositivoService = dispositivoService;
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
            Optional<Dispositivo> dispositivo = dispositivoService.autenticar(cabecera.substring(BEARER.length()).trim());
            if (dispositivo.isPresent()) {
                Dispositivo d = dispositivo.get();
                SecurityContext ctx = SecurityContextHolder.createEmptyContext();
                ctx.setAuthentication(new UsernamePasswordAuthenticationToken(
                        PREFIJO_PRINCIPAL + d.getId(),
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_DEVICE"))));
                SecurityContextHolder.setContext(ctx);
                request.setAttribute(ATRIBUTO_DEVICE_ID, d.getId());
                request.setAttribute(ATRIBUTO_PROPIETARIO, d.getPropietarioCorreo());
            } else {
                // Sin detalles en el log: no se registra el token recibido.
                logger.warn("Token de dispositivo no válido desde IP: " + request.getRemoteAddr());
            }
        }
        filterChain.doFilter(request, response);
    }
}
