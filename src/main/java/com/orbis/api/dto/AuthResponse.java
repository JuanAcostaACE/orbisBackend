package com.orbis.api.dto;

/**
 * DTO de respuesta para login y registro.
 * Nunca incluye el hash de contraseña.
 */
public record AuthResponse(
        Long   id,
        String name,
        String email,
        String message    // "Registro exitoso" / "Login exitoso"
) {}
