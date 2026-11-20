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
 *   - POST /auth/registro  → app.ratelimit.registro.*
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
    /** Login sin cookies para la app móvil: comparte el mismo límite que /auth/login. */
    static final String TOKEN_PATH    = "/auth/token";
    static final String REGISTRO_PATH = "/auth/registro";
    /** Registro de bastones con código de vinculación (D-002). Código de 39 bits: se limita por IP. */
    static final String DISPOSITIVO_REGISTRO_PATH = "/api/v1/dispositivos/registro";

    /** Cada cuántas entradas nuevas se limpian las ventanas caducadas. */
    private static final int LIMPIEZA_UMBRAL = 5_000;

    private final boolean enabled;
    private final Regla   login;
    private final Regla   registro;
    private final Regla   dispositivoRegistro;

    private final Map<String, Ventana> ventanas = new ConcurrentHashMap<>();

    public RateLimitFilter(
            @Value("${app.ratelimit.enabled:true}")                 boolean enabled,
            @Value("${app.ratelimit.login.max-intentos:10}")        int loginMax,
            @Value("${app.ratelimit.login.ventana-segundos:60}")    long loginVentana,
            @Value("${app.ratelimit.registro.max-intentos:10}")     int registroMax,
            @Value("${app.ratelimit.registro.ventana-segundos:3600}") long registroVentana,
            @Value("${app.ratelimit.dispositivo-registro.max-intentos:20}") int dispositivoMax,
            @Value("${app.ratelimit.dispositivo-registro.ventana-segundos:3600}") long dispositivoVentana) {
        this.enabled  = enabled;
        this.login    = new Regla("login",    loginMax,    loginVentana * 1000L);
        this.registro = new Regla("registro", registroMax, registroVentana * 1000L);
        this.dispositivoRegistro = new Regla("dispositivo-registro", dispositivoMax, dispositivoVentana * 1000L);
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest  request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain         filterChain
    ) throws ServletException, IOException {

        Regla regla = enabled ? reglaPara(request) : null;
        if (regla == null) {
            filterChain.doFilter(request, response);
            return;
        }

        long ahora = System.currentTimeMillis();
        String clave = regla.nombre + "|" + request.getRemoteAddr();

        Ventana ventana = ventanas.compute(clave, (k, actual) ->
                (actual == null || ahora - actual.inicioMs >= regla.ventanaMs)
                        ? new Ventana(ahora)
                        : actual);

        int intento = ventana.contador.incrementAndGet();

        if (ventanas.size() > LIMPIEZA_UMBRAL) {
            limpiarVentanasCaducadas(ahora);
        }

        if (intento > regla.maxIntentos) {
            long segundosRestantes = Math.max(1L,
                    (regla.ventanaMs - (ahora - ventana.inicioMs) + 999L) / 1000L);
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(segundosRestantes));
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(
                    "{\"status\":429,"
                  + "\"mensaje\":\"Demasiados intentos. Intenta de nuevo más tarde\","
                  + "\"timestamp\":\"" + LocalDateTime.now() + "\"}"
            );
            logger.warn("Límite de peticiones alcanzado (" + regla.nombre
                      + ") desde IP: " + request.getRemoteAddr());
            return;
        }

        filterChain.doFilter(request, response);
    }

    private Regla reglaPara(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return null;
        }
        String ruta = request.getRequestURI();
        if (LOGIN_PATH.equals(ruta) || TOKEN_PATH.equals(ruta)) return login;
        if (REGISTRO_PATH.equals(ruta)) return registro;
        if (DISPOSITIVO_REGISTRO_PATH.equals(ruta)) return dispositivoRegistro;
        return null;
    }

    private void limpiarVentanasCaducadas(long ahora) {
        long maxVentana = Math.max(Math.max(login.ventanaMs, registro.ventanaMs), dispositivoRegistro.ventanaMs);
        ventanas.entrySet().removeIf(e -> ahora - e.getValue().inicioMs >= maxVentana);
    }

    /** Configuración de un límite concreto. */
    private record Regla(String nombre, int maxIntentos, long ventanaMs) {}

    /** Contador de una ventana fija que empieza en inicioMs. */
    private static final class Ventana {
        final long inicioMs;
        final AtomicInteger contador = new AtomicInteger(0);

        Ventana(long inicioMs) {
            this.inicioMs = inicioMs;
        }
    }
}
