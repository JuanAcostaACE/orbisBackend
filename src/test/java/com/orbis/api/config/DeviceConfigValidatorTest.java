package com.orbis.api.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Combinaciones de APP_DEVICE_TOKEN / APP_DEVICE_OWNER_EMAIL.
 *
 * Regla: si el token está definido, el correo del propietario es obligatorio y
 * debe ser válido (fallo de arranque en caso contrario). Si el token está vacío,
 * la autenticación de dispositivo está deshabilitada y el correo es opcional
 * (se permite; si está definido, solo se registra un WARN).
 *
 * Valores de prueba ficticios: nunca se imprime ni afirma un token real.
 */
class DeviceConfigValidatorTest {

    private static final String FAKE_TOKEN = "test-token-combinacion-1";
    private static final String VALID_EMAIL = "dueno-prueba@test.com";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(DeviceConfigValidator.class);

    @Test
    @DisplayName("Combinación 1: token + owner válidos → el contexto arranca")
    void combination1_valid_token_and_owner_starts() {
        runner.withPropertyValues(
                        "app.device.token=" + FAKE_TOKEN,
                        "app.device.ownerEmail=" + VALID_EMAIL)
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(ctx).hasSingleBean(DeviceConfigValidator.class);
                });
    }

    @Test
    @DisplayName("Combinación 2: token definido sin owner → el arranque falla nombrando APP_DEVICE_OWNER_EMAIL")
    void combination2_token_without_owner_fails_startup() {
        runner.withPropertyValues("app.device.token=" + FAKE_TOKEN)
                .run(ctx -> {
                    assertThat(ctx).hasFailed();
                    assertThat(ctx.getStartupFailure()).hasRootCauseInstanceOf(IllegalStateException.class);
                    assertThat(ctx.getStartupFailure().getCause().getMessage())
                            .contains("APP_DEVICE_OWNER_EMAIL")
                            .doesNotContain(FAKE_TOKEN);
                });
    }

    @Test
    @DisplayName("Combinación 3: owner definido sin token → PERMITIDO (auth de dispositivo deshabilitada, no falla)")
    void combination3_owner_without_token_allowed_auth_disabled() {
        runner.withPropertyValues("app.device.ownerEmail=" + VALID_EMAIL)
                .run(ctx -> assertThat(ctx).hasNotFailed());
    }

    @Test
    @DisplayName("Combinación 4: token y owner vacíos → PERMITIDO (auth de dispositivo deshabilitada)")
    void combination4_both_empty_allowed_auth_disabled() {
        runner.run(ctx -> assertThat(ctx).hasNotFailed());
    }

    @Test
    @DisplayName("Combinación 5: token definido con owner de formato inválido → el arranque falla")
    void combination5_token_with_invalid_owner_email_fails_startup() {
        runner.withPropertyValues(
                        "app.device.token=" + FAKE_TOKEN,
                        "app.device.ownerEmail=no-es-un-correo")
                .run(ctx -> {
                    assertThat(ctx).hasFailed();
                    assertThat(ctx.getStartupFailure().getCause().getMessage())
                            .contains("APP_DEVICE_OWNER_EMAIL")
                            .doesNotContain("no-es-un-correo")
                            .doesNotContain(FAKE_TOKEN);
                });
    }

    @Test
    @DisplayName("Token solo con espacios se trata como vacío: owner ausente no falla")
    void blank_token_treated_as_disabled() {
        assertThatCode(() -> DeviceConfigValidator.validate("   ", ""))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validar: token definido y owner con espacios alrededor válido pasa")
    void validate_accepts_owner_with_surrounding_spaces() {
        assertThatCode(() -> DeviceConfigValidator.validate(FAKE_TOKEN, "  " + VALID_EMAIL + "  "))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validar: token definido y owner sin arroba lanza IllegalStateException sin valores")
    void validate_rejects_owner_without_at_sign() {
        assertThatThrownBy(() -> DeviceConfigValidator.validate(FAKE_TOKEN, "dueno.test.com"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_DEVICE_OWNER_EMAIL")
                .hasMessageNotContaining(FAKE_TOKEN)
                .hasMessageNotContaining("dueno.test.com");
    }
}
