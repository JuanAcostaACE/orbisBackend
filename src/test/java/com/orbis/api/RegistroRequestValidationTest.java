package com.orbis.api;

import com.orbis.api.dto.RegistroRequest;
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
class RegistroRequestValidationTest {

    static Validator validator;

    @BeforeAll
    static void init() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    private boolean valida(String pass) {
        return validator.validate(new RegistroRequest("Usuario", "u@test.com", pass)).isEmpty();
    }

    @Test
    @DisplayName("72 caracteres ASCII es válido")
    void ascii_72() {
        assertTrue(valida("a".repeat(72)));
    }

    @Test
    @DisplayName("73 caracteres es inválido")
    void ascii_73() {
        assertFalse(valida("a".repeat(73)));
    }

    @Test
    @DisplayName("40 caracteres con tilde (80 bytes) es inválido")
    void multibyte_supera_72_bytes() {
        assertFalse(valida("ñ".repeat(40)));
    }

    @Test
    @DisplayName("Menos de 8 caracteres es inválido")
    void muy_corta() {
        assertFalse(valida("corta"));
    }
}
