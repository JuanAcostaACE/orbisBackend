package com.orbis.api.dto;

import jakarta.validation.constraints.*;

/**
 * DTO para POST /auth/login
 */
public record LoginRequest(

        @NotBlank(message = "El correo es obligatorio")
        @Email(message = "El correo no tiene formato válido")
        String correo,

        @NotBlank(message = "La contraseña es obligatoria")
        String contrasena

) {}
