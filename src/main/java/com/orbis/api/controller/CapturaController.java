package com.orbis.api.controller;

import com.orbis.api.config.DispositivoAuthFilter;
import com.orbis.api.model.CapturaFotografia;
import com.orbis.api.service.CapturaException;
import com.orbis.api.service.CapturaService;
import com.orbis.api.service.ValidadorJpeg;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Map;

/**
 * Subida de fotografías desde el bastón (docs/ORBIS_DEVICE_PROTOCOL.md, secciones 4 y 5).
 *
 * Cuerpo binario image/jpeg; metadatos en cabeceras (X-Capture-Id obligatorio).
 * El propietario se resuelve del SecurityContext, nunca del cuerpo ni de la URL.
 *
 * Limitación de esta fase: la identidad del dispositivo sigue siendo el token compartido
 * de DeviceTokenFilter. La identidad por dispositivo (D-002) es un paso posterior.
 */
@RestController
@RequestMapping("/api/v1/dispositivos/capturas")
public class CapturaController {

    static final String HEADER_CAPTURE_ID = "X-Capture-Id";

    private final CapturaService capturaService;

    public CapturaController(CapturaService capturaService) {
        this.capturaService = capturaService;
    }

    public record CapturaRespuesta(String captureId, String estado, boolean duplicada) {
    }

    /** 202 si la captura es nueva; 200 si es un reintento idéntico de una captura existente. */
    @PostMapping
    public ResponseEntity<CapturaRespuesta> recibir(
            @RequestHeader(value = HEADER_CAPTURE_ID, required = false) String captureId,
            HttpServletRequest request,
            Authentication auth) throws IOException {

        ValidadorJpeg.validarTipo(request.getContentType());

        long declarado = request.getContentLengthLong();
        if (declarado > ValidadorJpeg.TAMANO_MAXIMO_BYTES) {
            throw new CapturaException(HttpStatus.PAYLOAD_TOO_LARGE, "IMAGEN_DEMASIADO_GRANDE");
        }
        // Lectura acotada: nunca se carga en memoria más de TAMANO_MAXIMO + 1 bytes.
        byte[] cuerpo = request.getInputStream()
                .readNBytes(ValidadorJpeg.TAMANO_MAXIMO_BYTES + 1);
        if (cuerpo.length > ValidadorJpeg.TAMANO_MAXIMO_BYTES) {
            throw new CapturaException(HttpStatus.PAYLOAD_TOO_LARGE, "IMAGEN_DEMASIADO_GRANDE");
        }

        CapturaService.Resultado resultado = capturaService.recibir(
                resolverCorreo(auth, request), captureId, cuerpo);
        CapturaFotografia captura = resultado.captura();
        CapturaRespuesta cuerpoRespuesta = new CapturaRespuesta(
                captura.getCaptureId(), captura.getEstado().name(), resultado.duplicada());

        return ResponseEntity
                .status(resultado.duplicada() ? HttpStatus.OK : HttpStatus.ACCEPTED)
                .body(cuerpoRespuesta);
    }

    /** Estado de una captura propia. Una captura ajena responde 404 igual que una inexistente. */
    @GetMapping("/{captureId}")
    public ResponseEntity<CapturaRespuesta> estado(
            @PathVariable String captureId,
            HttpServletRequest request,
            Authentication auth) {
        return capturaService.obtener(resolverCorreo(auth, request), captureId)
                .map(c -> ResponseEntity.ok(new CapturaRespuesta(
                        c.getCaptureId(), c.getEstado().name(), false)))
                .orElse(ResponseEntity.notFound().build());
    }

    @ExceptionHandler(CapturaException.class)
    public ResponseEntity<Map<String, String>> manejarCapturaInvalida(CapturaException e) {
        return ResponseEntity.status(e.getStatus()).body(Map.of("error", e.getCodigo()));
    }

    private String resolverCorreo(Authentication auth, HttpServletRequest request) {
        // Cualquier principal de dispositivo toma el propietario del atributo del filtro.
        if (auth != null && auth.getName().startsWith(DispositivoAuthFilter.PREFIJO_PRINCIPAL)) {
            Object owner = request.getAttribute("deviceOwnerEmail");
            if (owner instanceof String email && !email.isBlank()) {
                return email;
            }
            throw new IllegalStateException("Dispositivo autenticado sin propietario configurado");
        }
        return auth != null ? auth.getName() : "";
    }
}
