package com.orbis.api.controller;

import com.orbis.api.config.DispositivoAuthFilter;
import com.orbis.api.service.DispositivoException;
import com.orbis.api.service.DispositivoService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Gestión de bastones (docs/ORBIS_DEVICE_PROTOCOL.md, sección 5).
 *
 * Usuario web: POST /vinculaciones, DELETE /{deviceId}.
 * Bastón (token propio): POST /token/rotar, GET /config.
 * Sin autenticación: POST /registro (el código de vinculación es la prueba).
 */
@RestController
@RequestMapping("/api/v1/dispositivos")
public class DispositivoController {

    private final DispositivoService dispositivoService;

    public DispositivoController(DispositivoService dispositivoService) {
        this.dispositivoService = dispositivoService;
    }

    public record RegistroDispositivo(String codigo, String modeloHardware, String versionFirmware) {
    }

    public record RegistroRespuesta(String deviceId, String deviceToken, LocalDateTime tokenValidoHasta) {
    }

    public record TokenRespuesta(String deviceToken, LocalDateTime tokenValidoHasta) {
    }

    public record CodigoRespuesta(String codigo, LocalDateTime expiraEn) {
    }

    @PostMapping("/vinculaciones")
    public ResponseEntity<CodigoRespuesta> crearVinculacion(Authentication auth) {
        DispositivoService.CodigoGenerado generado = dispositivoService.crearVinculacion(auth.getName());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new CodigoRespuesta(generado.codigo(), generado.expiraEn()));
    }

    @PostMapping("/registro")
    public ResponseEntity<RegistroRespuesta> registrar(@RequestBody RegistroDispositivo cuerpo) {
        DispositivoService.Registro r = dispositivoService.registrar(
                cuerpo.codigo(), cuerpo.modeloHardware(), cuerpo.versionFirmware());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new RegistroRespuesta(r.deviceId(), r.token(), r.tokenValidoHasta()));
    }

    @PostMapping("/token/rotar")
    public ResponseEntity<TokenRespuesta> rotar(HttpServletRequest request) {
        Object deviceId = request.getAttribute(DispositivoAuthFilter.ATRIBUTO_DEVICE_ID);
        if (!(deviceId instanceof String id)) {
            throw new DispositivoException(HttpStatus.UNAUTHORIZED, "NO_AUTORIZADO");
        }
        DispositivoService.Rotacion r = dispositivoService.rotar(id);
        return ResponseEntity.ok(new TokenRespuesta(r.token(), r.tokenValidoHasta()));
    }

    /** Parámetros de captura que el bastón debe usar. Valores propuestos; pendientes de validar en hardware. */
    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> config(HttpServletRequest request) {
        if (request.getAttribute(DispositivoAuthFilter.ATRIBUTO_DEVICE_ID) == null) {
            throw new DispositivoException(HttpStatus.UNAUTHORIZED, "NO_AUTORIZADO");
        }
        return ResponseEntity.ok(Map.of(
                "resolucion", "VGA",
                "calidadJpeg", 13,
                "tamanoMaximoBytes", 524288,
                "versionApiMinima", "1"));
    }

    @DeleteMapping("/{deviceId}")
    public ResponseEntity<Void> revocar(@PathVariable String deviceId, Authentication auth) {
        return dispositivoService.revocar(auth.getName(), deviceId)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    @ExceptionHandler(DispositivoException.class)
    public ResponseEntity<Map<String, String>> manejarError(DispositivoException e) {
        return ResponseEntity.status(e.getStatus()).body(Map.of("error", e.getCodigo()));
    }
}
