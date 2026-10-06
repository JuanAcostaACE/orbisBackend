package com.smartcane.api.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Configuración CORS centralizada.
 * En desarrollo: permite cualquier origen (*).
 * En producción: reemplazar los orígenes con la URL real de Vercel.
 *
 * Nota: @CrossOrigin("*") en el controller sigue activo y complementa esto.
 * Esta clase da control más fino sobre métodos y headers permitidos.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns(
                        "http://localhost:*",           // Desarrollo local
                        "https://*.vercel.app",         // Frontend en Vercel
                        "https://*.ngrok-free.app",     // Ngrok para pruebas
                        "https://*.ngrok.io"            // Ngrok legacy
                )
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);
    }
}
