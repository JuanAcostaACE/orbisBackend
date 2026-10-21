package com.orbis.api.controller;

import com.orbis.api.config.DeviceTokenFilter;
import com.orbis.api.dto.EventoRequest;
import com.orbis.api.dto.EventoResponse;
import com.orbis.api.dto.PaginaResponse;
import com.orbis.api.service.EventoService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Controller de eventos.
 *
 * Autenticación dual:
 *  - Usuario web (ROLE_USER): propietario = Authentication.getName() (correo)
 *  - Dispositivo ESP32 (ROLE_DEVICE): propietario = atributo "deviceOwnerEmail"
 *    inyectado por DeviceTokenFilter desde la variable de entorno DEVICE_OWNER_EMAIL.
 *
 * Paginación (GET /api/v1/eventos):
 *  - page:  número de página (0-indexed, default: 0)
 *  - size:  elementos por página (default: 20, máximo: 100)
 *
 * Contrato HTTP:
 *  - POST  /api/v1/eventos        → 201 Created  + EventoResponse
 *  - GET   /api/v1/eventos        → 200 OK        + PaginaResponse<EventoResponse>
 *  - GET   /api/v1/eventos/{id}   → 200/404
 *  - GET   /api/v1/eventos/estado → 200 OK        + estado actual (patrón State)
 */
@RestController
@RequestMapping("/api/v1/eventos")
public class EventoController {

    private final EventoService eventoService;

    public EventoController(EventoService eventoService) {
        this.eventoService = eventoService;
    }

    /**
     * Recibe un evento del ESP32 o del simulador web.
     * El propietario nunca proviene del body: se resuelve del SecurityContext.
     */
    @PostMapping
    public ResponseEntity<EventoResponse> recibirEvento(
            @Valid @RequestBody EventoRequest request,
            Authentication auth,
            HttpServletRequest httpRequest) {

        String correo = resolverCorreo(auth, httpRequest);
        EventoResponse response = eventoService.gestionarEvento(request, correo);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Lista paginada de eventos del usuario autenticado.
     * Un usuario no puede ver eventos de otro modificando page/size.
     */
    @GetMapping
    public ResponseEntity<PaginaResponse<EventoResponse>> listarEventos(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication auth,
            HttpServletRequest httpRequest) {

        String correo = resolverCorreo(auth, httpRequest);
        return ResponseEntity.ok(
                eventoService.listarPorUsuarioPaginado(correo, page, size)
        );
    }

    /**
     * Obtiene un evento por ID verificando que pertenezca al usuario autenticado.
     */
    @GetMapping("/{id}")
    public ResponseEntity<EventoResponse> obtenerEvento(
            @PathVariable Long id,
            Authentication auth,
            HttpServletRequest httpRequest) {

        String correo = resolverCorreo(auth, httpRequest);
        return eventoService.buscarPorIdYUsuario(id, correo)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Estado actual del sistema según el patrón State.
     * Útil para monitoreo y el simulador.
     */
    @GetMapping("/estado")
    public ResponseEntity<Map<String, String>> obtenerEstado() {
        return ResponseEntity.ok(Map.of("estadoActual", eventoService.getEstadoActual()));
    }

    // ────────────────────────────────────────────────────────────────────
    // Helper: resuelve el correo del propietario según el tipo de autenticación
    // ────────────────────────────────────────────────────────────────────

    /**
     * Para ROLE_DEVICE: el correo viene del atributo de request inyectado por DeviceTokenFilter.
     * Para ROLE_USER:   el correo viene del principal del SecurityContext (Authentication.getName()).
     * Nunca del cuerpo del request.
     */
    private String resolverCorreo(Authentication auth, HttpServletRequest httpRequest) {
        if (auth != null && DeviceTokenFilter.DEVICE_PRINCIPAL.equals(auth.getName())) {
            Object ownerEmail = httpRequest.getAttribute("deviceOwnerEmail");
            if (ownerEmail instanceof String email && !email.isBlank()) {
                return email;
            }
            throw new IllegalStateException(
                    "Dispositivo autenticado pero DEVICE_OWNER_EMAIL no está configurado. " +
                    "Configura la variable de entorno APP_DEVICE_OWNER_EMAIL en Railway.");
        }
        return auth != null ? auth.getName() : "";
    }
}