package com.orbis.api.service;

import com.orbis.api.dto.EventoRequest;
import com.orbis.api.dto.EventoResponse;
import com.orbis.api.dto.PaginaResponse;
import com.orbis.api.factory.StrategyFactory;
import com.orbis.api.model.RegistroEvento;
import com.orbis.api.model.Usuario;
import com.orbis.api.repository.RegistroEventoRepository;
import com.orbis.api.repository.UsuarioRepository;
import com.orbis.api.state.ContextoSistema;
import com.orbis.api.strategy.ModoProcesamientoStrategy;
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
 *   - GET /api/v1/eventos?page=0&size=20 → devuelve PaginaResponse<EventoResponse>
 *   - Tamaño máximo: 100 elementos por página.
 *   - imagenUrl excluida de EventoResponse (evita payloads de MBs).
 */
@Service
public class EventoService {

    static final int PAGE_SIZE_DEFAULT = 20;
    static final int PAGE_SIZE_MAX     = 100;

    private final StrategyFactory             strategyFactory;
    private final ContextoSistema             contextoSistema;
    private final RegistroEventoRepository    registroEventoRepository;
    private final UsuarioRepository           usuarioRepository;

    public EventoService(StrategyFactory          strategyFactory,
                         ContextoSistema          contextoSistema,
                         RegistroEventoRepository registroEventoRepository,
                         UsuarioRepository        usuarioRepository) {
        this.strategyFactory          = strategyFactory;
        this.contextoSistema          = contextoSistema;
        this.registroEventoRepository = registroEventoRepository;
        this.usuarioRepository        = usuarioRepository;
    }

    // ────────────────────────────────────────────────────────────────────
    // Creación
    // ────────────────────────────────────────────────────────────────────

    /**
     * Crea y procesa un nuevo evento.
     *
     * @param request        DTO validado por {@code @Valid} en el controller.
     * @param correoUsuario  Correo del propietario. Para dispositivos ESP32 viene del
     *                       atributo de request {@code deviceOwnerEmail}; para usuarios
     *                       web, del {@code Authentication.getName()} del SecurityContext.
     *                       Nunca proviene del cuerpo del request.
     */
    @Transactional
    public EventoResponse gestionarEvento(EventoRequest request, String correoUsuario) {
        if (correoUsuario == null || correoUsuario.isBlank()) {
            throw new IllegalArgumentException("No se pudo determinar el propietario del evento");
        }

        Usuario propietario = resolverUsuario(correoUsuario);

        RegistroEvento evento = new RegistroEvento();
        evento.setModo(request.modo().toUpperCase());
        evento.setDistanciaCm(request.distanciaCm());
        evento.setEtiquetasIA(sanitizarEtiquetas(request.etiquetasIA()));
        evento.setPropietario(propietario);
        evento.setImagenUrl(null); // Nunca aceptado del cliente

        // 1. State
        contextoSistema.procesarEvento(evento);
        // 2. Factory
        ModoProcesamientoStrategy estrategia = strategyFactory.obtenerEstrategia(evento.getModo());
        // 3. Strategy
        RegistroEvento guardado = estrategia.procesar(evento);

        return toResponse(guardado);
    }

    // ────────────────────────────────────────────────────────────────────
    // Lectura paginada (usuarios web)
    // ────────────────────────────────────────────────────────────────────

    /**
     * Lista los eventos del usuario autenticado con paginación.
     * Un usuario no puede ver eventos de otro modificando parámetros.
     *
     * @param correoUsuario correo del usuario autenticado (del SecurityContext).
     * @param page          número de página basado en 0.
     * @param size          elementos por página (máx: PAGE_SIZE_MAX).
     */
    @Transactional(readOnly = true)
    public PaginaResponse<EventoResponse> listarPorUsuarioPaginado(
            String correoUsuario, int page, int size) {

        int sizeSanitizado = Math.min(Math.max(size, 1), PAGE_SIZE_MAX);
        int pageSanitizado = Math.max(page, 0);

        Usuario u = resolverUsuario(correoUsuario);

        var pageable = PageRequest.of(
                pageSanitizado,
                sizeSanitizado,
                Sort.by(Sort.Direction.DESC, "fechaHora")
        );

        var paginaJpa = registroEventoRepository
                .findAllByPropietarioId(u.getId(), pageable)
                .map(this::toResponse);

        return PaginaResponse.of(paginaJpa);
    }

    /** Lista sin paginar — uso interno y para el ESP32. */
    @Transactional(readOnly = true)
    public List<EventoResponse> listarPorUsuario(String correoUsuario) {
        Usuario u = resolverUsuario(correoUsuario);
        return registroEventoRepository
                .findAllByPropietarioIdOrderByFechaHoraDesc(u.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /** Lookup seguro: verifica que el evento pertenezca al usuario autenticado. */
    @Transactional(readOnly = true)
    public Optional<EventoResponse> buscarPorIdYUsuario(Long id, String correoUsuario) {
        Usuario u = resolverUsuario(correoUsuario);
        return registroEventoRepository
                .findByIdAndPropietarioId(id, u.getId())
                .map(this::toResponse);
    }

    /** Estado actual del sistema (patrón State). */
    public String getEstadoActual() {
        return contextoSistema.getNombreEstadoActual();
    }

    // ────────────────────────────────────────────────────────────────────
    // Helpers privados
    // ────────────────────────────────────────────────────────────────────

    private Usuario resolverUsuario(String correo) {
        return usuarioRepository.findByCorreo(correo.toLowerCase())
                .orElseThrow(() -> new UsernameNotFoundException(
                        "Usuario propietario no encontrado: " + correo));
    }

    /**
     * Mapea RegistroEvento → EventoResponse.
     * imagenUrl excluida intencionalmente (evita payloads de MBs).
     */
    private EventoResponse toResponse(RegistroEvento e) {
        String obstEtiqueta = e.getObstaculo() != null ? e.getObstaculo().getEtiqueta()    : null;
        String obstNivel    = e.getObstaculo() != null ? e.getObstaculo().getNivelPeligro() : null;
        return new EventoResponse(
                e.getId(),
                e.getModo(),
                e.getDistanciaCm(),
                e.getEtiquetasIA(),
                e.getFechaHora(),
                obstEtiqueta,
                obstNivel
        );
    }

    /**
     * Sanitiza etiquetas del cliente:
     * - Descarta las que empiecen con ERROR (no persistir errores de IA).
     * - Trunca a 2 000 chars como defensa adicional.
     */
    private String sanitizarEtiquetas(String etiquetas) {
        if (etiquetas == null || etiquetas.isBlank()) return null;
        String limpio = etiquetas.trim();
        if (limpio.startsWith("ERROR")) return null;
        return limpio.length() > 2000 ? limpio.substring(0, 2000) : limpio;
    }
}