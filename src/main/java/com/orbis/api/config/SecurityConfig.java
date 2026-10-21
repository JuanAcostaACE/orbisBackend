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
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * Configuración central de Spring Security.
 *
 * Mecanismo: sesión de servidor con cookie HttpOnly.
 *   - El servidor mantiene la sesión en memoria (H2) o en base de datos (Railway).
 *   - La cookie JSESSIONID se marca HttpOnly y Secure (en producción).
 *   - CSRF se habilita con la política cookie-to-header para SPAs
 *     (el frontend debe leer XSRF-TOKEN y enviarlo en X-XSRF-TOKEN).
 *
 * Endpoints públicos: /auth/registro, /auth/login, /health.
 * Todos los demás requieren autenticación.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final OrbisUserDetailsService userDetailsService;
    private final CorsConfigurationSource corsConfigurationSource;

    public SecurityConfig(OrbisUserDetailsService userDetailsService,
                          CorsConfigurationSource corsConfigurationSource) {
        this.userDetailsService      = userDetailsService;
        this.corsConfigurationSource = corsConfigurationSource;
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
            // CORS: usa el bean CorsConfigurationSource definido en CorsConfig
            .cors(cors -> cors.configurationSource(corsConfigurationSource))

            // CSRF: habilitado con cookie accesible al JS para SPA cross-origin
            // El frontend debe leer XSRF-TOKEN y enviar X-XSRF-TOKEN en cada mutación
            .csrf(csrf -> csrf
                .csrfTokenRepository(
                    org.springframework.security.web.csrf.CookieCsrfTokenRepository.withHttpOnlyFalse()
                )
                // Ignorar CSRF en registro y login (aún no hay sesión)
                .ignoringRequestMatchers("/auth/**")
            )

            // Gestión de sesión
            .sessionManagement(sm -> sm
                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                .maximumSessions(5)
            )

            // Autorización de rutas
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/auth/**", "/health", "/h2-console/**").permitAll()
                .anyRequest().authenticated()
            )

            // UserDetailsService
            .userDetailsService(userDetailsService)

            // Sin redirección: respuestas 401/403 en JSON para API REST
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((req, res, e) -> {
                    res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    res.setContentType("application/json;charset=UTF-8");
                    res.getWriter().write(
                        "{\"status\":401,\"mensaje\":\"Se requiere autenticación\"}"
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

            // H2 console (solo en desarrollo local)
            .headers(h -> h.frameOptions(fo -> fo.sameOrigin()));

        return http.build();
    }
}
