package com.orbis.api.controller;

import com.orbis.api.config.DeviceTokenFilter;
import com.orbis.api.dto.EventRequest;
import com.orbis.api.dto.EventResponse;
import com.orbis.api.dto.PageResponse;
import com.orbis.api.service.EventService;
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
 * Paginación (GET /api/v1/events):
 *  - page:  número de página (0-indexed, default: 0)
 *  - size:  elementos por página (default: 20, máximo: 100)
 *
 * Contrato HTTP:
 *  - POST  /api/v1/events        → 201 Created  + EventResponse
 *  - GET   /api/v1/events        → 200 OK        + PageResponse<EventResponse>
 *  - GET   /api/v1/events/{id}   → 200/404
 *  - GET   /api/v1/events/status → 200 OK        + estado actual (patrón State)
 */
@RestController
@RequestMapping("/api/v1/events")
public class EventController {

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    /**
     * Recibe un evento del ESP32 o del simulador web.
     * El propietario nunca proviene del body: se resuelve del SecurityContext.
     */
    @PostMapping
    public ResponseEntity<EventResponse> receiveEvent(
            @Valid @RequestBody EventRequest request,
            Authentication auth,
            HttpServletRequest httpRequest) {

        String email = resolveEmail(auth, httpRequest);
        EventResponse response = eventService.handleEvent(request, email);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Lista paginada de eventos del usuario autenticado.
     * Un usuario no puede ver eventos de otro modificando page/size.
     */
    @GetMapping
    public ResponseEntity<PageResponse<EventResponse>> listEvents(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication auth,
            HttpServletRequest httpRequest) {

        String email = resolveEmail(auth, httpRequest);
        return ResponseEntity.ok(
                eventService.listByUserPaginated(email, page, size)
        );
    }

    /**
     * Obtiene un evento por ID verificando que pertenezca al usuario autenticado.
     */
    @GetMapping("/{id}")
    public ResponseEntity<EventResponse> getEventById(
            @PathVariable Long id,
            Authentication auth,
            HttpServletRequest httpRequest) {

        String email = resolveEmail(auth, httpRequest);
        return eventService.findByIdAndUser(id, email)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Estado actual del sistema según el patrón State.
     * Útil para monitoreo y el simulador.
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, String>> getStatus() {
        return ResponseEntity.ok(Map.of("currentState", eventService.getCurrentState()));
    }

    // ────────────────────────────────────────────────────────────────────
    // Helper: resuelve el correo del propietario según el tipo de autenticación
    // ────────────────────────────────────────────────────────────────────

    /**
     * Para ROLE_DEVICE: el correo viene del atributo de request inyectado por DeviceTokenFilter.
     * Para ROLE_USER:   el correo viene del principal del SecurityContext (Authentication.getName()).
     * Nunca del cuerpo del request.
     */
    private String resolveEmail(Authentication auth, HttpServletRequest httpRequest) {
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