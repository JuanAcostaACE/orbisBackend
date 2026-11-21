package com.orbis.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.*;

import java.nio.charset.StandardCharsets;

/**
 * DTO para POST /auth/register
 */
public record RegisterRequest(

        @NotBlank(message = "El nombre es obligatorio")
        @Size(min = 2, max = 60, message = "El nombre debe tener entre 2 y 60 caracteres")
        String name,

        @NotBlank(message = "El correo es obligatorio")
        @Email(message = "El correo no tiene formato válido")
        @Size(max = 120, message = "El correo no puede superar 120 caracteres")
        String email,

        @NotBlank(message = "La contraseña es obligatoria")
        @Size(min = 8, max = 72, message = "La contraseña debe tener entre 8 y 72 caracteres")
        String password

) {

    /** BCrypt solo procesa 72 bytes; con tildes o emojis 72 caracteres pueden superar ese límite. */
    static final int MAX_BYTES_BCRYPT = 72;

    @JsonIgnore
    @AssertTrue(message = "La contraseña es demasiado larga (máximo 72 bytes)")
    public boolean isPasswordWithinBcryptLimit() {
        return password == null
                || password.getBytes(StandardCharsets.UTF_8).length <= MAX_BYTES_BCRYPT;
    }
}
