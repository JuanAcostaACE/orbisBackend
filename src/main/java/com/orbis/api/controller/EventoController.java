package com.orbis.api.controller;

import com.orbis.api.dto.EventoRequest;
import com.orbis.api.dto.EventoResponse;
import com.orbis.api.service.EventoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Controller de eventos.
 *
 * Cambios respecto a la versión anterior:
 *  - Recibe EventoRequest (DTO con @Valid) en lugar de la entidad JPA.
 *  - Devuelve EventoResponse (sin imagenUrl, sin entidad expuesta).
 *  - Obtiene el propietario del SecurityContext, no del JSON del cliente.
 *  - Devuelve 201 Created en POST.
 *  - El @CrossOrigin("*") fue eliminado; la política CORS está en CorsConfig.
 *  - Todos los endpoints requieren autenticación (configurado en SecurityConfig).
 */
@RestController
@RequestMapping("/api/v1/eventos")
public class EventoController {

    private final EventoService eventoService;

    public EventoController(EventoService eventoService) {
        this.eventoService = eventoService;
    }

    /** Recibe eventos del ESP32 o el simulador (modo PASIVO o ACTIVO). */
    @PostMapping
    public ResponseEntity<EventoResponse> recibirEvento(
            @Valid @RequestBody EventoRequest request,
            Authentication auth) {
        EventoResponse response = eventoService.gestionarEvento(request, auth.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** Lista eventos del usuario autenticado — sin imágenes base64. */
    @GetMapping
    public ResponseEntity<List<EventoResponse>> listarEventos(Authentication auth) {
        return ResponseEntity.ok(eventoService.listarPorUsuario(auth.getName()));
    }

    /** Obtiene un evento por ID, verificando propiedad. */
    @GetMapping("/{id}")
    public ResponseEntity<EventoResponse> obtenerEvento(
            @PathVariable Long id,
            Authentication auth) {
        return eventoService.buscarPorIdYUsuario(id, auth.getName())
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /** Estado actual del sistema según el patrón State. */
    @GetMapping("/estado")
    public ResponseEntity<Map<String, String>> obtenerEstado() {
        return ResponseEntity.ok(Map.of("estadoActual", eventoService.getEstadoActual()));
    }
}