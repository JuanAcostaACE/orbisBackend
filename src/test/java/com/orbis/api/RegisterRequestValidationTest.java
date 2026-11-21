package com.orbis.api;

import com.orbis.api.dto.RegisterRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Límite de 72 bytes de BCrypt en la contraseña de registro.
 */
class RegisterRequestValidationTest {

    static Validator validator;

    @BeforeAll
    static void init() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    private boolean valid(String pass) {
        return validator.validate(new RegisterRequest("Usuario", "u@test.com", pass)).isEmpty();
    }

    @Test
    @DisplayName("72 caracteres ASCII es válido")
    void ascii_72() {
        assertTrue(valid("a".repeat(72)));
    }

    @Test
    @DisplayName("73 caracteres es inválido")
    void ascii_73() {
        assertFalse(valid("a".repeat(73)));
    }

    @Test
    @DisplayName("40 caracteres con tilde (80 bytes) es inválido")
    void multibyte_exceeds_72_bytes() {
        assertFalse(valid("ñ".repeat(40)));
    }

    @Test
    @DisplayName("Menos de 8 caracteres es inválido")
    void too_short() {
        assertFalse(valid("corta"));
    }
}
