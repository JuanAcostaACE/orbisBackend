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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * Filtro de autenticación para dispositivos físicos (ESP32).
 *
 * SEGURIDAD:
 *
 * 1. Comparación en tiempo constante (timing-safe):
 *    Usa {@link MessageDigest#isEqual} con bytes UTF-8 para evitar ataques de
 *    timing (side-channel). Una comparación con {@code String.equals} puede
 *    retornar en distinto tiempo dependiendo de cuántos bytes coincidan,
 *    permitiendo que un atacante deduzca el token mediante peticiones repetidas.
 *
 * 2. Rechazo seguro si el token no está configurado:
 *    Si {@code APP_DEVICE_TOKEN} está vacío o en blanco, este filtro NO autentica
 *    ninguna petición de dispositivo. Cualquier petición con X-Device-Token hacia
 *    un endpoint protegido recibirá 401 por el EntryPoint de Spring Security.
 *    No hay NullPointerException ni fallo silencioso.
 *
 * 3. Sin logging del token:
 *    El valor del token NUNCA se imprime en logs ni en respuestas de error.
 *    Solo se registra el resultado (autenticado / rechazado) con el IP del cliente.
 *
 * 4. Contexto del dispositivo:
 *    El propietario de los eventos del dispositivo se resuelve exclusivamente
 *    desde el atributo de request {@code deviceOwnerEmail}, que viene de la
 *    variable de entorno {@code APP_DEVICE_OWNER_EMAIL}, nunca del body del request.
 *
 * CONFIGURACIÓN (Railway Dashboard > Variables):
 *   APP_DEVICE_TOKEN       = <cadena aleatoria 32+ chars>
 *   APP_DEVICE_OWNER_EMAIL = correo@ejemplo.com
 */
@Component
public class DeviceTokenFilter extends OncePerRequestFilter {

    /** Nombre del header que el ESP32 debe enviar. */
    public static final String HEADER_NAME = "X-Device-Token";

    /** Principal sintético que identifica al dispositivo en el SecurityContext. */
    public static final String DEVICE_PRINCIPAL = "device:esp32";

    /**
     * Token secreto en bytes UTF-8 pre-calculados.
     * Almacenar como bytes evita re-encodear en cada request.
     * Si vacío → autenticación de dispositivo deshabilitada.
     */
    private final byte[] deviceTokenBytes;

    @Value("${app.device.ownerEmail:}")
    private String deviceOwnerEmail;

    public DeviceTokenFilter(@Value("${app.device.token:}") String deviceToken) {
        // Pre-calcular bytes una sola vez al arrancar
        // Si el token está vacío, se guarda un array de longitud 0 (seguro)
        this.deviceTokenBytes = (deviceToken == null || deviceToken.isBlank())
                ? new byte[0]
                : deviceToken.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest  request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain         filterChain
    ) throws ServletException, IOException {

        // Dispositivo deshabilitado: continuar sin autenticar
        if (deviceTokenBytes.length == 0) {
            filterChain.doFilter(request, response);
            return;
        }

        // Solo actuar si el contexto aún no está autenticado (no sobreescribir sesión web)
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            String header = request.getHeader(HEADER_NAME);

            if (header != null && !header.isBlank()) {
                byte[] headerBytes = header.getBytes(StandardCharsets.UTF_8);

                // Comparación en tiempo constante (timing-safe)
                boolean tokenValido = MessageDigest.isEqual(deviceTokenBytes, headerBytes);

                if (tokenValido) {
                    SecurityContext ctx = SecurityContextHolder.createEmptyContext();
                    ctx.setAuthentication(new UsernamePasswordAuthenticationToken(
                            DEVICE_PRINCIPAL,
                            null,
                            List.of(new SimpleGrantedAuthority("ROLE_DEVICE"))
                    ));
                    SecurityContextHolder.setContext(ctx);
                    request.setAttribute("deviceOwnerEmail", deviceOwnerEmail);

                    logger.debug("Dispositivo autenticado desde IP: " + request.getRemoteAddr());
                } else {
                    // Token inválido recibido — log mínimo sin revelar valores
                    logger.warn("X-Device-Token inválido recibido desde IP: " + request.getRemoteAddr()
                              + " (longitud header: " + headerBytes.length
                              + ", longitud esperada: " + deviceTokenBytes.length + ")");
                }
            }
        }

        filterChain.doFilter(request, response);
    }
}
