package com.orbis.api.dto;

/** Respuesta de POST /auth/token. Contrato consumido por la app móvil. */
public record TokenSesionResponse(
        String accessToken,
        String tokenType,
        long   expiresIn,
        String correo,
        String nombre
) {}
