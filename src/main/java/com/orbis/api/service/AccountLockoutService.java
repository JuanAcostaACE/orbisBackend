package com.orbis.api.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Límite de intentos fallidos de login POR CUENTA (clave = correo normalizado).
 *
 * Funciona aunque el correo no exista: así una cuenta bloqueada y una inexistente
 * se comportan igual y no se revela si el correo está registrado.
 *
 * Ventana fija: al alcanzar {@code max} fallos dentro de la ventana, la cuenta queda bloqueada
 * hasta que termina la ventana. Un login correcto borra el contador.
 *
 * Limitación: el estado vive en memoria del proceso. Con varias instancias cada una cuenta
 * aparte; ver README (requiere almacén compartido, decisión del propietario).
 */
@Service
public class AccountLockoutService {

    /** Cada cuántas entradas nuevas se limpian las ventanas caducadas. */
    private static final int CLEANUP_THRESHOLD = 5_000;

    private final int  max;
    private final long windowMs;

    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public AccountLockoutService(
            @Value("${app.ratelimit.account.max-attempts:5}")         int  max,
            @Value("${app.ratelimit.account.window-seconds:900}")   long windowSeconds) {
        this.max      = max;
        this.windowMs = windowSeconds * 1000L;
    }

    /**
     * Segundos restantes de bloqueo para la cuenta, o 0 si no está bloqueada.
     * @param correo correo ya normalizado (ver AuthService.normalizeEmail)
     */
    public long lockoutSeconds(String email) {
        Window v = windows.get(email);
        if (v == null) return 0L;

        long now = System.currentTimeMillis();
        long elapsed = now - v.startMs;
        if (elapsed >= windowMs) {
            windows.remove(email, v);
            return 0L;
        }
        if (v.failures.get() < max) return 0L;

        return Math.max(1L, TimeUnit.MILLISECONDS.toSeconds(windowMs - elapsed + 999L));
    }

    /** Registra un intento fallido para la cuenta. */
    public void recordFailure(String email) {
        long now = System.currentTimeMillis();
        windows.compute(email, (k, current) -> {
            if (current == null || now - current.startMs >= windowMs) {
                return new Window(now);
            }
            current.failures.incrementAndGet();
            return current;
        });
        if (windows.size() > CLEANUP_THRESHOLD) {
            clearExpired(now);
        }
    }

    /** Borra el contador de la cuenta (login correcto). */
    public void clear(String email) {
        windows.remove(email);
    }

    private void clearExpired(long now) {
        Iterator<Map.Entry<String, Window>> it = windows.entrySet().iterator();
        while (it.hasNext()) {
            if (now - it.next().getValue().startMs >= windowMs) {
                it.remove();
            }
        }
    }

    /** Contador de una ventana fija que empieza en startMs. Arranca con el primer fallo. */
    private static final class Window {
        final long startMs;
        final AtomicInteger failures = new AtomicInteger(1);

        Window(long startMs) {
            this.startMs = startMs;
        }
    }
}
