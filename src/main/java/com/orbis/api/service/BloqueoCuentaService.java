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
public class BloqueoCuentaService {

    /** Cada cuántas entradas nuevas se limpian las ventanas caducadas. */
    private static final int LIMPIEZA_UMBRAL = 5_000;

    private final int  max;
    private final long ventanaMs;

    private final Map<String, Ventana> ventanas = new ConcurrentHashMap<>();

    public BloqueoCuentaService(
            @Value("${app.ratelimit.cuenta.max-intentos:5}")         int  max,
            @Value("${app.ratelimit.cuenta.ventana-segundos:900}")   long ventanaSegundos) {
        this.max      = max;
        this.ventanaMs = ventanaSegundos * 1000L;
    }

    /**
     * Segundos restantes de bloqueo para la cuenta, o 0 si no está bloqueada.
     * @param correo correo ya normalizado (ver AuthService.normalizarCorreo)
     */
    public long segundosBloqueo(String correo) {
        Ventana v = ventanas.get(correo);
        if (v == null) return 0L;

        long ahora = System.currentTimeMillis();
        long transcurrido = ahora - v.inicioMs;
        if (transcurrido >= ventanaMs) {
            ventanas.remove(correo, v);
            return 0L;
        }
        if (v.fallos.get() < max) return 0L;

        return Math.max(1L, TimeUnit.MILLISECONDS.toSeconds(ventanaMs - transcurrido + 999L));
    }

    /** Registra un intento fallido para la cuenta. */
    public void registrarFallo(String correo) {
        long ahora = System.currentTimeMillis();
        ventanas.compute(correo, (k, actual) -> {
            if (actual == null || ahora - actual.inicioMs >= ventanaMs) {
                return new Ventana(ahora);
            }
            actual.fallos.incrementAndGet();
            return actual;
        });
        if (ventanas.size() > LIMPIEZA_UMBRAL) {
            limpiarCaducadas(ahora);
        }
    }

    /** Borra el contador de la cuenta (login correcto). */
    public void limpiar(String correo) {
        ventanas.remove(correo);
    }

    private void limpiarCaducadas(long ahora) {
        Iterator<Map.Entry<String, Ventana>> it = ventanas.entrySet().iterator();
        while (it.hasNext()) {
            if (ahora - it.next().getValue().inicioMs >= ventanaMs) {
                it.remove();
            }
        }
    }

    /** Contador de una ventana fija que empieza en inicioMs. Arranca con el primer fallo. */
    private static final class Ventana {
        final long inicioMs;
        final AtomicInteger fallos = new AtomicInteger(1);

        Ventana(long inicioMs) {
            this.inicioMs = inicioMs;
        }
    }
}
