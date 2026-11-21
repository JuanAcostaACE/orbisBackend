package com.orbis.api.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Validación de arranque (fail-fast) de la autenticación de dispositivo ESP32.
 *
 * REGLA:
 *  - APP_DEVICE_TOKEN vacío o en blanco → autenticación de dispositivo DESHABILITADA.
 *    APP_DEVICE_OWNER_EMAIL puede estar vacío. Si está definido, se registra un
 *    aviso (WARN) porque no tiene efecto; el arranque NO falla.
 *  - APP_DEVICE_TOKEN definido → APP_DEVICE_OWNER_EMAIL es obligatorio y debe tener
 *    formato de correo válido. Si falta o es inválido, el contexto NO arranca.
 *
 * El mensaje de error nombra la variable, pero NUNCA incluye el valor del token
 * ni el del correo.
 */
@Component
public class DeviceConfigValidator implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(DeviceConfigValidator.class);

    static final String VAR_TOKEN = "APP_DEVICE_TOKEN";
    static final String VAR_OWNER = "APP_DEVICE_OWNER_EMAIL";

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private final String deviceToken;
    private final String deviceOwnerEmail;

    public DeviceConfigValidator(@Value("${app.device.token:}") String deviceToken,
                                 @Value("${app.device.ownerEmail:}") String deviceOwnerEmail) {
        this.deviceToken = deviceToken;
        this.deviceOwnerEmail = deviceOwnerEmail;
    }

    @Override
    public void afterPropertiesSet() {
        validate(deviceToken, deviceOwnerEmail);
    }

    /**
     * Aplica la regla de configuración. Lanza IllegalStateException si es inválida.
     * Expuesto como método estático para poder probarlo sin contexto Spring.
     */
    static void validate(String token, String ownerEmail) {
        if (isBlankValue(token)) {
            if (!isBlankValue(ownerEmail)) {
                log.warn("{} está definido pero {} está vacío: la autenticación de dispositivo está deshabilitada.",
                        VAR_OWNER, VAR_TOKEN);
            }
            return;
        }

        if (isBlankValue(ownerEmail)) {
            throw new IllegalStateException(
                    "Configuración de dispositivo incompleta: la variable " + VAR_OWNER + " es obligatoria "
                    + "cuando " + VAR_TOKEN + " está definida. Define " + VAR_OWNER
                    + " con el correo del propietario de los eventos del dispositivo.");
        }

        if (!EMAIL_PATTERN.matcher(ownerEmail.trim()).matches()) {
            throw new IllegalStateException(
                    "Configuración de dispositivo inválida: la variable " + VAR_OWNER
                    + " no tiene un formato de correo válido.");
        }
    }

    private static boolean isBlankValue(String value) {
        return value == null || value.isBlank();
    }
}
