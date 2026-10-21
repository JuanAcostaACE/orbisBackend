package com.orbis.api.config;

import com.orbis.api.service.OrbisUserDetailsService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.http.HttpMethod;

/**
 * Configuración central de Spring Security para ORBIS.
 *
 * Mecanismo dual de autenticación:
 *  1. Usuarios web → sesión de servidor con cookie HttpOnly (JSESSIONID).
 *  2. Dispositivos ESP32 → header X-Device-Token (ROLE_DEVICE).
 *
 * Flujo de filtros (orden importante):
 *   DeviceTokenFilter → UsernamePasswordAuthenticationFilter → ...
 *
 * CSRF:
 *  - Habilitado para mutaciones de usuarios web (SPA lee XSRF-TOKEN).
 *  - Deshabilitado para /auth/** (sin sesión previa) y /api/v1/eventos (POST del ESP32).
 *
 * Roles:
 *  - ROLE_USER  → usuarios registrados (web).
 *  - ROLE_DEVICE → dispositivos autenticados por token.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final OrbisUserDetailsService  userDetailsService;
    private final CorsConfigurationSource  corsConfigurationSource;
    private final DeviceTokenFilter        deviceTokenFilter;

    public SecurityConfig(OrbisUserDetailsService userDetailsService,
                          CorsConfigurationSource corsConfigurationSource,
                          DeviceTokenFilter       deviceTokenFilter) {
        this.userDetailsService     = userDetailsService;
        this.corsConfigurationSource = corsConfigurationSource;
        this.deviceTokenFilter      = deviceTokenFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource))

            // CSRF con cookie legible por el SPA (SameSite=Strict lo maneja el navegador)
            .csrf(csrf -> csrf
                .csrfTokenRepository(
                    org.springframework.security.web.csrf.CookieCsrfTokenRepository.withHttpOnlyFalse()
                )
                // Ignorar CSRF para:
                //   /auth/**       → aún no hay sesión en registro/login
                //   /api/v1/eventos POST → ESP32 usa token propio (no navegador)
                //   /health        → endpoint de monitoreo
                .ignoringRequestMatchers("/auth/**", "/api/v1/eventos", "/health")
            )

            // Sesión solo para usuarios web (no para el dispositivo)
            .sessionManagement(sm -> sm
                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                .maximumSessions(5)
            )

            // Autorización de rutas
            .authorizeHttpRequests(auth -> auth
                // Endpoints públicos mínimos
                .requestMatchers("/auth/**", "/health", "/h2-console/**").permitAll()
                // Ingesta del ESP32: requiere ROLE_DEVICE (token) o ROLE_USER (sesión)
                .requestMatchers(HttpMethod.POST, "/api/v1/eventos").hasAnyRole("DEVICE", "USER")
                // Todo lo demás: solo usuarios web autenticados
                .anyRequest().authenticated()
            )

            .userDetailsService(userDetailsService)

            // DeviceTokenFilter ANTES del filtro estándar de usuario/contraseña
            .addFilterBefore(deviceTokenFilter, UsernamePasswordAuthenticationFilter.class)

            // Respuestas 401/403 en JSON (sin redirección a login HTML)
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((req, res, e) -> {
                    res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    res.setContentType("application/json;charset=UTF-8");
                    res.getWriter().write(
                        "{\"status\":401,\"mensaje\":\"Se requiere autenticaci\u00f3n\"}"
                    );
                })
                .accessDeniedHandler((req, res, e) -> {
                    res.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    res.setContentType("application/json;charset=UTF-8");
                    res.getWriter().write(
                        "{\"status\":403,\"mensaje\":\"Acceso no autorizado\"}"
                    );
                })
            )

            // H2 console: solo desarrollo local (frame embebido)
            .headers(h -> h.frameOptions(fo -> fo.sameOrigin()));

        return http.build();
    }
}
