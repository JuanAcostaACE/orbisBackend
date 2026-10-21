package com.orbis.api.service;

import com.orbis.api.dto.EventoRequest;
import com.orbis.api.dto.EventoResponse;
import com.orbis.api.factory.StrategyFactory;
import com.orbis.api.model.RegistroEvento;
import com.orbis.api.model.Usuario;
import com.orbis.api.repository.RegistroEventoRepository;
import com.orbis.api.repository.UsuarioRepository;
import com.orbis.api.state.ContextoSistema;
import com.orbis.api.strategy.ModoProcesamientoStrategy;
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
 * Recibe EventoRequest (DTO validado) y devuelve EventoResponse (sin imagenUrl).
 * El propietario del evento se asigna aquí desde el correo del usuario autenticado;
 * el cliente nunca puede forzarlo.
 */
@Service
public class EventoService {

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

    /**
     * Crea y procesa un nuevo evento.
     * @param request        DTO validado por @Valid en el controller.
     * @param correoUsuario  Correo del usuario autenticado (del SecurityContext).
     */
    @Transactional
    public EventoResponse gestionarEvento(EventoRequest request, String correoUsuario) {
        Usuario propietario = usuarioRepository.findByCorreo(correoUsuario)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado: " + correoUsuario));

        // Mapear DTO → entidad (sin id, sin fechaHora, sin propietario aún)
        RegistroEvento evento = new RegistroEvento();
        evento.setModo(request.modo().toUpperCase());
        evento.setDistanciaCm(request.distanciaCm());
        evento.setEtiquetasIA(sanitizarEtiquetas(request.etiquetasIA()));
        evento.setPropietario(propietario);
        // imagenUrl: nunca se acepta del cliente
        evento.setImagenUrl(null);

        // 1. State: transicionar al estado correcto
        contextoSistema.procesarEvento(evento);

        // 2. Factory Method: crear la strategy adecuada para el modo
        ModoProcesamientoStrategy estrategia = strategyFactory.obtenerEstrategia(evento.getModo());

        // 3. Strategy: ejecutar el procesamiento (Command + Facade + Observer internamente)
        RegistroEvento guardado = estrategia.procesar(evento);

        return toResponse(guardado);
    }

    /** Lista los eventos del usuario autenticado (no puede ver los de otros). */
    @Transactional(readOnly = true)
    public List<EventoResponse> listarPorUsuario(String correoUsuario) {
        Usuario u = usuarioRepository.findByCorreo(correoUsuario)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado: " + correoUsuario));
        return registroEventoRepository
                .findAllByPropietarioIdOrderByFechaHoraDesc(u.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /** Busca un evento por id, verificando que pertenezca al usuario autenticado. */
    @Transactional(readOnly = true)
    public Optional<EventoResponse> buscarPorIdYUsuario(Long id, String correoUsuario) {
        Usuario u = usuarioRepository.findByCorreo(correoUsuario)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado: " + correoUsuario));
        return registroEventoRepository
                .findByIdAndPropietarioId(id, u.getId())
                .map(this::toResponse);
    }

    /** Expone el estado actual del sistema para el controller (útil para debugging). */
    public String getEstadoActual() {
        return contextoSistema.getNombreEstadoActual();
    }

    // ----------------------------------------------------------------
    // Helpers privados
    // ----------------------------------------------------------------

    /**
     * Mapea RegistroEvento → EventoResponse.
     * imagenUrl se omite intencionalmente para no exponer base64 de MBs.
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
     * Limpia etiquetas recibidas del cliente:
     * - Rechaza las que empiecen por ERROR (no persistir mensajes de error de IA).
     * - Trunca a 2000 chars como defensa adicional.
     */
    private String sanitizarEtiquetas(String etiquetas) {
        if (etiquetas == null || etiquetas.isBlank()) return null;
        String limpio = etiquetas.trim();
        if (limpio.startsWith("ERROR")) return null;
        return limpio.length() > 2000 ? limpio.substring(0, 2000) : limpio;
    }
}