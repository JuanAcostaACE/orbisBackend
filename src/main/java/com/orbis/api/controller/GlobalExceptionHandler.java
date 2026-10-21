package com.orbis.api.controller;

import com.orbis.api.facade.VisionException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Manejador global de excepciones.
 * Nunca expone trazas internas, consultas SQL, hashes ni secretos.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** Errores de validación de @Valid — 400 con detalle de campos. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(
            MethodArgumentNotValidException ex) {
        String detalle = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest()
                .body(errorBody(400, "Datos de entrada inválidos: " + detalle));
    }

    /** Lógica de negocio violada (modo inválido, correo duplicado, etc.) — 400. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(
            IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(errorBody(400, ex.getMessage()));
    }

    /** Credenciales incorrectas — 401 genérico (no revelar si el correo existe). */
    @ExceptionHandler({BadCredentialsException.class})
    public ResponseEntity<Map<String, Object>> handleBadCredentials(Exception ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(errorBody(401, "Credenciales incorrectas"));
    }

    /** Usuario no encontrado tras autenticarse (caso raro) — 404. */
    @ExceptionHandler(UsernameNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleUserNotFound(
            UsernameNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(errorBody(404, "Recurso no encontrado"));
    }

    /** Error de visión artificial — 503. */
    @ExceptionHandler(VisionException.class)
    public ResponseEntity<Map<String, Object>> handleVision(VisionException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(errorBody(503, "Servicio de visión artificial no disponible"));
    }

    /** Operación no implementada — 501. */
    @ExceptionHandler(UnsupportedOperationException.class)
    public ResponseEntity<Map<String, Object>> handleNotImplemented(
            UnsupportedOperationException ex) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                .body(errorBody(501, ex.getMessage()));
    }

    /** Fallback: error interno sin detalle (no filtra información). */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(errorBody(500, "Error interno del servidor"));
    }

    private Map<String, Object> errorBody(int status, String mensaje) {
        return Map.of(
                "status",    status,
                "mensaje",   mensaje,
                "timestamp", LocalDateTime.now().toString()
        );
    }
}
