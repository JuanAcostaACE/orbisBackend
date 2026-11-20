package com.orbis.api.controller;

import com.orbis.api.facade.VisionException;
import com.orbis.api.service.CuentaBloqueadaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Manejador global de excepciones.
 * Nunca expone trazas internas, consultas SQL, hashes ni secretos.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Cuerpo ausente, vacío o ilegible (p. ej. JSON mal formado) — 400 genérico. */
    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestPartException.class})
    public ResponseEntity<Map<String, Object>> handleCuerpoInvalido(Exception ex) {
        return ResponseEntity.badRequest()
                .body(errorBody(400, "Cuerpo de la petición inválido o vacío"));
    }

    /** Parámetro de ruta o consulta con tipo incorrecto — 400 genérico. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTipoParametro(
            MethodArgumentTypeMismatchException ex) {
        return ResponseEntity.badRequest()
                .body(errorBody(400, "Parámetro de petición inválido"));
    }

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

    /** Cuenta bloqueada por demasiados intentos — 429, mismo cuerpo que el limitador por IP. */
    @ExceptionHandler(CuentaBloqueadaException.class)
    public ResponseEntity<Map<String, Object>> handleCuentaBloqueada(CuentaBloqueadaException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", String.valueOf(ex.getSegundosRestantes()))
                .body(errorBody(429, "Demasiados intentos. Intenta de nuevo más tarde"));
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

    /** Ruta inexistente — 404 genérico (no es un error interno). */
    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<Map<String, Object>> handleRutaInexistente(Exception ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(errorBody(404, "Recurso no encontrado"));
    }

    /** Fallback: error interno. Se registra en log; el cliente no recibe detalle. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
        log.error("Error interno no controlado", ex);
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
