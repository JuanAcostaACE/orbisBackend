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

    private static final String TOKEN_FALSO = "test-token-combinacion-1";
    private static final String CORREO_VALIDO = "dueno-prueba@test.com";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(DeviceConfigValidator.class);

    @Test
    @DisplayName("Combinación 1: token + owner válidos → el contexto arranca")
    void combinacion1_token_y_owner_validos_arranca() {
        runner.withPropertyValues(
                        "app.device.token=" + TOKEN_FALSO,
                        "app.device.ownerEmail=" + CORREO_VALIDO)
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(ctx).hasSingleBean(DeviceConfigValidator.class);
                });
    }

    @Test
    @DisplayName("Combinación 2: token definido sin owner → el arranque falla nombrando APP_DEVICE_OWNER_EMAIL")
    void combinacion2_token_sin_owner_falla_arranque() {
        runner.withPropertyValues("app.device.token=" + TOKEN_FALSO)
                .run(ctx -> {
                    assertThat(ctx).hasFailed();
                    assertThat(ctx.getStartupFailure()).hasRootCauseInstanceOf(IllegalStateException.class);
                    assertThat(ctx.getStartupFailure().getCause().getMessage())
                            .contains("APP_DEVICE_OWNER_EMAIL")
                            .doesNotContain(TOKEN_FALSO);
                });
    }

    @Test
    @DisplayName("Combinación 3: owner definido sin token → PERMITIDO (auth de dispositivo deshabilitada, no falla)")
    void combinacion3_owner_sin_token_permitido_auth_deshabilitada() {
        runner.withPropertyValues("app.device.ownerEmail=" + CORREO_VALIDO)
                .run(ctx -> assertThat(ctx).hasNotFailed());
    }

    @Test
    @DisplayName("Combinación 4: token y owner vacíos → PERMITIDO (auth de dispositivo deshabilitada)")
    void combinacion4_ambos_vacios_permitido_auth_deshabilitada() {
        runner.run(ctx -> assertThat(ctx).hasNotFailed());
    }

    @Test
    @DisplayName("Combinación 5: token definido con owner de formato inválido → el arranque falla")
    void combinacion5_token_con_owner_email_invalido_falla_arranque() {
        runner.withPropertyValues(
                        "app.device.token=" + TOKEN_FALSO,
                        "app.device.ownerEmail=no-es-un-correo")
                .run(ctx -> {
                    assertThat(ctx).hasFailed();
                    assertThat(ctx.getStartupFailure().getCause().getMessage())
                            .contains("APP_DEVICE_OWNER_EMAIL")
                            .doesNotContain("no-es-un-correo")
                            .doesNotContain(TOKEN_FALSO);
                });
    }

    @Test
    @DisplayName("Token solo con espacios se trata como vacío: owner ausente no falla")
    void token_en_blanco_se_trata_como_deshabilitado() {
        assertThatCode(() -> DeviceConfigValidator.validar("   ", ""))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validar: token definido y owner con espacios alrededor válido pasa")
    void validar_acepta_owner_con_espacios_alrededor() {
        assertThatCode(() -> DeviceConfigValidator.validar(TOKEN_FALSO, "  " + CORREO_VALIDO + "  "))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validar: token definido y owner sin arroba lanza IllegalStateException sin valores")
    void validar_rechaza_owner_sin_arroba() {
        assertThatThrownBy(() -> DeviceConfigValidator.validar(TOKEN_FALSO, "dueno.test.com"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_DEVICE_OWNER_EMAIL")
                .hasMessageNotContaining(TOKEN_FALSO)
                .hasMessageNotContaining("dueno.test.com");
    }
}
