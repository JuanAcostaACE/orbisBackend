package com.orbis.api.dto;

/**
 * DTO de respuesta para login y registro.
 * Nunca incluye el hash de contraseña.
 */
public record AuthResponse(
        Long   id,
        String nombre,
        String correo,
        String mensaje    // "Registro exitoso" / "Login exitoso"
) {}
