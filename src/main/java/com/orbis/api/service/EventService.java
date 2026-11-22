package com.orbis.api.service;

import com.orbis.api.dto.EventRequest;
import com.orbis.api.dto.EventResponse;
import com.orbis.api.dto.PageResponse;
import com.orbis.api.factory.StrategyFactory;
import com.orbis.api.model.EventRecord;
import com.orbis.api.model.UserAccount;
import com.orbis.api.repository.EventRecordRepository;
import com.orbis.api.repository.UserAccountRepository;
import com.orbis.api.state.SystemContext;
import com.orbis.api.strategy.ProcessingModeStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Servicio central de eventos.
 *
 * Orquesta los patrones de diseño en este orden:
 *   1. State   — actualiza el estado del sistema según el modo del evento
 *   2. Factory — obtiene la strategy correcta via StrategyFactory
 *   3. Strategy — procesa el evento (internamente usa Command + Observer + Facade)
 *
 * Autenticación dual:
 *   - Usuarios web: propietario resuelto desde el correo del SecurityContext.
 *   - Dispositivos ESP32: propietario resuelto desde el correo configurado en
 *     la variable de entorno DEVICE_OWNER_EMAIL (nunca desde el cuerpo del request).
 *
 * Paginación:
 *   - GET /api/v1/events?page=0&size=20 → devuelve PageResponse<EventResponse>
 *   - Tamaño máximo: 100 elementos por página.
 *   - imageUrl excluida de EventResponse (evita payloads de MBs).
 */
@Service
public class EventService {

    private static final Logger log = LoggerFactory.getLogger(EventService.class);

    static final int PAGE_SIZE_DEFAULT = 20;
    static final int PAGE_SIZE_MAX     = 100;

    private final StrategyFactory             strategyFactory;
    private final SystemContext             systemContext;
    private final EventRecordRepository    eventRecordRepository;
    private final UserAccountRepository           userAccountRepository;

    public EventService(StrategyFactory          strategyFactory,
                         SystemContext          systemContext,
                         EventRecordRepository eventRecordRepository,
                         UserAccountRepository        userAccountRepository) {
        this.strategyFactory          = strategyFactory;
        this.systemContext          = systemContext;
        this.eventRecordRepository = eventRecordRepository;
        this.userAccountRepository        = userAccountRepository;
    }

    // ────────────────────────────────────────────────────────────────────
    // Creación
    // ────────────────────────────────────────────────────────────────────

    /**
     * Crea y procesa un nuevo evento.
     *
     * @param request        DTO validado por {@code @Valid} en el controller.
     * @param userEmail  Correo del propietario. Para dispositivos ESP32 viene del
     *                       atributo de request {@code deviceOwnerEmail}; para usuarios
     *                       web, del {@code Authentication.getName()} del SecurityContext.
     *                       Nunca proviene del cuerpo del request.
     */
    @Transactional
    public EventResponse handleEvent(EventRequest request, String userEmail) {
        if (userEmail == null || userEmail.isBlank()) {
            throw new IllegalArgumentException("No se pudo determinar el propietario del evento");
        }

        UserAccount owner = resolveUser(userEmail);

        EventRecord event = new EventRecord();
        event.setMode(request.mode().toUpperCase());
        event.setDistanceCm(request.distanceCm());
        event.setAiLabels(sanitizeLabels(request.aiLabels()));
        event.setOwner(owner);
        event.setImageUrl(null); // Nunca aceptado del cliente

        // 1. State
        systemContext.processEvent(event);
        // 2. Factory
        ProcessingModeStrategy strategy = strategyFactory.getStrategy(event.getMode());
        // 3. Strategy
        EventRecord saved = strategy.process(event);

        return toResponse(saved);
    }

    // ────────────────────────────────────────────────────────────────────
    // Lectura paginada (usuarios web)
    // ────────────────────────────────────────────────────────────────────

    /**
     * Lista los eventos del usuario autenticado con paginación.
     * Un usuario no puede ver eventos de otro modificando parámetros.
     *
     * @param userEmail correo del usuario autenticado (del SecurityContext).
     * @param page          número de página basado en 0.
     * @param size          elementos por página (máx: PAGE_SIZE_MAX).
     */
    @Transactional(readOnly = true)
    public PageResponse<EventResponse> listByUserPaginated(
            String userEmail, int page, int size) {

        int sanitizedSize = Math.min(Math.max(size, 1), PAGE_SIZE_MAX);
        int sanitizedPage = Math.max(page, 0);

        UserAccount u = resolveUser(userEmail);

        var pageable = PageRequest.of(
                sanitizedPage,
                sanitizedSize,
                Sort.by(Sort.Direction.DESC, "createdAt")
        );

        var jpaPage = eventRecordRepository
                .findAllByOwnerId(u.getId(), pageable)
                .map(this::toResponse);

        return PageResponse.of(jpaPage);
    }

    /** Lista sin paginar — uso interno y para el ESP32. */
    @Transactional(readOnly = true)
    public List<EventResponse> listByUser(String userEmail) {
        UserAccount u = resolveUser(userEmail);
        return eventRecordRepository
                .findAllByOwnerIdOrderByCreatedAtDesc(u.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /** Lookup seguro: verifica que el evento pertenezca al usuario autenticado. */
    @Transactional(readOnly = true)
    public Optional<EventResponse> findByIdAndUser(Long id, String userEmail) {
        UserAccount u = resolveUser(userEmail);
        return eventRecordRepository
                .findByIdAndOwnerId(id, u.getId())
                .map(this::toResponse);
    }

    /** Estado actual del sistema (patrón State). */
    public String getCurrentState() {
        return systemContext.getCurrentStateName();
    }

    // ────────────────────────────────────────────────────────────────────
    // Helpers privados
    // ────────────────────────────────────────────────────────────────────

    private UserAccount resolveUser(String email) {
        return userAccountRepository.findByEmail(email.toLowerCase())
                .orElseThrow(() -> {
                    // Sin el correo ni el token en el log: revisar APP_DEVICE_OWNER_EMAIL y el registro del propietario
                    log.error("Propietario de evento no registrado; el evento no se guardó.");
                    return new UsernameNotFoundException(
                            "Usuario propietario no encontrado: " + email);
                });
    }

    /**
     * Mapea EventRecord → EventResponse.
     * imageUrl excluida intencionalmente (evita payloads de MBs).
     */
    private EventResponse toResponse(EventRecord e) {
        String obstLabel = e.getObstacle() != null ? e.getObstacle().getLabel()    : null;
        String obstLevel    = e.getObstacle() != null ? e.getObstacle().getDangerLevel() : null;
        return new EventResponse(
                e.getId(),
                e.getMode(),
                e.getDistanceCm(),
                e.getAiLabels(),
                e.getCreatedAt(),
                obstLabel,
                obstLevel
        );
    }

    /**
     * Sanitiza etiquetas del cliente:
     * - Descarta las que empiecen con ERROR (no persistir errores de IA).
     * - Trunca a 2 000 chars como defensa adicional.
     */
    private String sanitizeLabels(String labels) {
        if (labels == null || labels.isBlank()) return null;
        String cleaned = labels.trim();
        if (cleaned.startsWith("ERROR")) return null;
        return cleaned.length() > 2000 ? cleaned.substring(0, 2000) : cleaned;
    }
}