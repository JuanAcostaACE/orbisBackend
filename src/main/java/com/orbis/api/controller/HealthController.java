package com.orbis.api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Health check endpoint.
 * Railway y cualquier plataforma de despliegue usan este endpoint
 * para verificar que el servicio está vivo y respondiendo.
 * URL: GET /health
 */
@RestController
@RequestMapping("/health")
public class HealthController {

    @GetMapping
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status",    "UP",
                "servicio",  "ORBIS Backend",
                "timestamp", LocalDateTime.now().toString()
        ));
    }
}
