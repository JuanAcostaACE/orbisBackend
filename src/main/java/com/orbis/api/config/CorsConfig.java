package com.orbis.api.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuración CORS centralizada.
 *
 * Publica un bean {@code CorsConfigurationSource} que es consumido tanto por
 * Spring Security (vía SecurityConfig) como por Spring MVC.
 *
 * IMPORTANTE: allowedCredentials=true es INCOMPATIBLE con allowedOrigins("*").
 * Por eso se usan orígenes explícitos vía variables de entorno.
 *
 * Variables de entorno en Railway:
 *   APP_CORS_ORIGIN  — URL exacta del frontend en producción (ej: https://orbis.vercel.app)
 *   APP_CORS_ORIGIN2 — URL alternativa (opcional)
 */
@Configuration
public class CorsConfig {

    @Value("${app.cors.origin:}")
    private String corsOrigin;

    @Value("${app.cors.origin2:}")
    private String corsOrigin2;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        List<String> allowedOrigins = buildAllowedOrigins();

        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("X-XSRF-TOKEN")); // SPA necesita leer el token CSRF
        config.setAllowCredentials(true);                   // Requerido para la cookie JSESSIONID
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    private List<String> buildAllowedOrigins() {
        List<String> origins = new ArrayList<>();
        // Siempre permitir localhost para desarrollo
        origins.add("http://localhost:3000");
        origins.add("http://localhost:8080");
        origins.add("http://localhost:5173");

        // Agregar URL de producción si están configuradas
        if (corsOrigin != null && !corsOrigin.isBlank()) {
            origins.add(corsOrigin.trim());
        }
        if (corsOrigin2 != null && !corsOrigin2.isBlank()) {
            origins.add(corsOrigin2.trim());
        }
        return origins;
    }
}
