package com.orbis.api.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Activa las tareas programadas del analizador de capturas (worker y retención de imágenes).
 * Propiedades: app.analisis.activo, app.analisis.retencion-horas, app.analisis.intervalo-ms,
 * app.analisis.retardo-inicial-ms, app.analisis.limpieza-ms.
 */
@Configuration
@EnableScheduling
public class AnalisisConfig {
}
