package com.smartcane.api.controller;

import com.smartcane.api.model.RegistroEvento;
import com.smartcane.api.service.EventoService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/eventos")
@CrossOrigin(origins = "*") // Importante para cuando conectes el Frontend en Vercel
public class EventoController {

    private final EventoService eventoService;

    public EventoController(EventoService eventoService) {
        this.eventoService = eventoService;
    }

    /** Recibe eventos del ESP32 (modo PASIVO o ACTIVO) */
    @PostMapping
    public ResponseEntity<RegistroEvento> recibirEventoEsp32(@RequestBody RegistroEvento request) {
        RegistroEvento eventoProcesado = eventoService.gestionarEvento(request);
        return ResponseEntity.ok(eventoProcesado);
    }

    /** Lista todos los eventos — consumido por el frontend */
    @GetMapping
    public ResponseEntity<List<RegistroEvento>> listarEventos() {
        return ResponseEntity.ok(eventoService.listarTodos());
    }

    /** Obtiene un evento específico por ID */
    @GetMapping("/{id}")
    public ResponseEntity<RegistroEvento> obtenerEvento(@PathVariable Long id) {
        return eventoService.buscarPorId(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /** Estado actual del sistema según el patrón State — útil para la demo */
    @GetMapping("/estado")
    public ResponseEntity<Map<String, String>> obtenerEstado() {
        return ResponseEntity.ok(Map.of("estadoActual", eventoService.getEstadoActual()));
    }
}