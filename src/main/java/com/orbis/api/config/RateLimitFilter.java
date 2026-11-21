package com.orbis.api.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Limitador de peticiones por IP con ventana fija, en memoria (sin librerías externas).
 *
 * Protege solo los endpoints sensibles a fuerza bruta o abuso:
 *   - POST /auth/login     → app.ratelimit.login.*
 *   - POST /auth/register  → app.ratelimit.register.*
 *
 * Cada intento (correcto o no) cuenta dentro de la ventana. Al superar el máximo
 * responde 429 con el mismo cuerpo JSON que el resto de la API ({status, mensaje, timestamp})
 * y la cabecera Retry-After.
 *
 * Limitaciones conocidas:
 *   - El contador vive en memoria del proceso: con varias instancias cada una cuenta aparte.
 *   - Usa request.getRemoteAddr(). Este filtro NO lee cabeceras. El cliente real lo resuelve
 *     Tomcat (RemoteIpValve, server.tomcat.remoteip.*) solo cuando la conexión directa viene de
 *     un proxy de confianza; X-Real-IP de otros pares se ignora y X-Forwarded-For nunca se usa.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    static final String LOGIN_PATH    = "/auth/login";
    static final String REGISTER_PATH = "/auth/register";

    /** Cada cuántas entradas nuevas se limpian las ventanas caducadas. */
    private static final int CLEANUP_THRESHOLD = 5_000;

    private final boolean enabled;
    private final Rule   login;
    private final Rule   register;

    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public RateLimitFilter(
            @Value("${app.ratelimit.enabled:true}")                 boolean enabled,
            @Value("${app.ratelimit.login.max-attempts:10}")        int loginMax,
            @Value("${app.ratelimit.login.window-seconds:60}")    long loginWindow,
            @Value("${app.ratelimit.register.max-attempts:10}")     int registerMax,
            @Value("${app.ratelimit.register.window-seconds:3600}") long registerWindow) {
        this.enabled  = enabled;
        this.login    = new Rule("login",    loginMax,    loginWindow * 1000L);
        this.register = new Rule("register", registerMax, registerWindow * 1000L);
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest  request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain         filterChain
    ) throws ServletException, IOException {

        Rule rule = enabled ? ruleFor(request) : null;
        if (rule == null) {
            filterChain.doFilter(request, response);
            return;
        }

        long now = System.currentTimeMillis();
        String key = rule.name + "|" + request.getRemoteAddr();

        Window window = windows.compute(key, (k, current) ->
                (current == null || now - current.startMs >= rule.windowMs)
                        ? new Window(now)
                        : current);

        int attempt = window.counter.incrementAndGet();

        if (windows.size() > CLEANUP_THRESHOLD) {
            clearExpiredWindows(now);
        }

        if (attempt > rule.maxAttempts) {
            long remainingSeconds = Math.max(1L,
                    (rule.windowMs - (now - window.startMs) + 999L) / 1000L);
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(remainingSeconds));
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(
                    "{\"status\":429,"
                  + "\"message\":\"Demasiados intentos. Intenta de nuevo más tarde\","
                  + "\"timestamp\":\"" + LocalDateTime.now() + "\"}"
            );
            logger.warn("Límite de peticiones alcanzado (" + rule.name
                      + ") desde IP: " + request.getRemoteAddr());
            return;
        }

        filterChain.doFilter(request, response);
    }

    private Rule ruleFor(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return null;
        }
        String uri = request.getRequestURI();
        if (LOGIN_PATH.equals(uri))    return login;
        if (REGISTER_PATH.equals(uri)) return register;
        return null;
    }

    private void clearExpiredWindows(long now) {
        long maxWindow = Math.max(login.windowMs, register.windowMs);
        windows.entrySet().removeIf(e -> now - e.getValue().startMs >= maxWindow);
    }

    /** Configuración de un límite concreto. */
    private record Rule(String name, int maxAttempts, long windowMs) {}

    /** Contador de una ventana fija que empieza en startMs. */
    private static final class Window {
        final long startMs;
        final AtomicInteger counter = new AtomicInteger(0);

        Window(long startMs) {
            this.startMs = startMs;
        }
    }
}
