package com.orbis.api.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Filtro de autenticación para dispositivos físicos (ESP32).
 *
 * El ESP32 no puede usar una sesión de navegador (cookies HttpOnly / CSRF).
 * En su lugar, envía el header {@code X-Device-Token} con un token secreto
 * configurado como variable de entorno {@code DEVICE_TOKEN}.
 *
 * Cuando el token es válido, se establece un {@code Authentication} con el
 * principal {@code "device:esp32"} y el rol {@code ROLE_DEVICE}.
 * El backend asigna el propietario del evento mediante el campo
 * {@code DEVICE_OWNER_EMAIL} (correo de un usuario registrado).
 *
 * SEGURIDAD:
 *  - El token debe tener al menos 32 caracteres aleatorios.
 *  - Nunca incluir el token en el código fuente ni en archivos versionados.
 *  - En hardware real, almacenar en NVS (nvs_flash) del ESP32, no en el sketch.
 *  - En Wokwi (simulación) se puede pasar como constante para pruebas locales.
 *  - Rotar el token si se sospecha compromiso; revocar basta con cambiar la var de entorno.
 */
@Component
public class DeviceTokenFilter extends OncePerRequestFilter {

    /** Nombre del header que el ESP32 debe enviar. */
    public static final String HEADER_NAME = "X-Device-Token";

    /** Principal sintético que identifica al dispositivo en el SecurityContext. */
    public static final String DEVICE_PRINCIPAL = "device:esp32";

    /**
     * Token secreto. Configurar en Railway:  DEVICE_TOKEN=<valor aleatorio 32+ chars>
     * Valor por defecto vacío → autenticación de dispositivo deshabilitada.
     */
    @Value("${app.device.token:}")
    private String deviceToken;

    /**
     * Correo del usuario propietario de los eventos del dispositivo.
     * Debe corresponder a un usuario registrado en la base de datos.
     * Configurar en Railway: DEVICE_OWNER_EMAIL=admin@ejemplo.com
     */
    @Value("${app.device.ownerEmail:}")
    private String deviceOwnerEmail;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest  request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain         filterChain
    ) throws ServletException, IOException {

        // Solo actuar si el deviceToken está configurado y el contexto aún no está autenticado
        if (!deviceToken.isBlank() && SecurityContextHolder.getContext().getAuthentication() == null) {
            String header = request.getHeader(HEADER_NAME);
            if (deviceToken.equals(header)) {
                SecurityContext ctx = SecurityContextHolder.createEmptyContext();
                ctx.setAuthentication(new UsernamePasswordAuthenticationToken(
                        DEVICE_PRINCIPAL,
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_DEVICE"))
                ));
                SecurityContextHolder.setContext(ctx);

                // Propagar el correo del propietario para que EventoService lo use
                request.setAttribute("deviceOwnerEmail", deviceOwnerEmail);
            }
        }

        filterChain.doFilter(request, response);
    }
}
