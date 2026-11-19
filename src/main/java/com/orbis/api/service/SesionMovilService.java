package com.orbis.api.service;

import com.orbis.api.model.SesionMovil;
import com.orbis.api.repository.SesionMovilRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Tokens de acceso para la app móvil (Bearer, sin cookies), caducidad 8 h, sin refresh token.
 * Reutiliza la generación de tokens (32 bytes SecureRandom, base64url) y el hash SHA-256 de
 * DispositivoService. El token en claro nunca se guarda ni se registra.
 */
@Service
public class SesionMovilService {

    public static final Duration VALIDEZ = Duration.ofHours(8);

    public record Emision(String accessToken, LocalDateTime expiraEn) {
    }

    private final SesionMovilRepository sesiones;

    public SesionMovilService(SesionMovilRepository sesiones) {
        this.sesiones = sesiones;
    }

    /** Emite un token nuevo para el correo. Se devuelve en claro solo en este resultado. */
    @Transactional
    public Emision crear(String correo) {
        String token = DispositivoService.generarToken();
        LocalDateTime ahora = LocalDateTime.now();
        LocalDateTime expira = ahora.plus(VALIDEZ);

        SesionMovil sesion = new SesionMovil();
        sesion.setId(UUID.randomUUID().toString());
        sesion.setTokenHash(DispositivoService.sha256Hex(token));
        sesion.setCorreo(correo);
        sesion.setCreadoEn(ahora);
        sesion.setExpiraEn(expira);
        sesion.setRevocado(false);
        sesiones.save(sesion);
        return new Emision(token, expira);
    }

    /** Devuelve la sesión si el token existe, no está revocado y no ha caducado. */
    @Transactional(readOnly = true)
    public Optional<SesionMovil> autenticar(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        return sesiones.findByTokenHash(DispositivoService.sha256Hex(token))
                .filter(s -> !s.isRevocado())
                .filter(s -> LocalDateTime.now().isBefore(s.getExpiraEn()));
    }

    /** Revoca la sesión: la siguiente autenticación con ese token falla. */
    @Transactional
    public void revocar(String id) {
        sesiones.findById(id).ifPresent(s -> {
            s.setRevocado(true);
            sesiones.save(s);
        });
    }
}
