package com.orbis.api.service;

import com.orbis.api.model.CodigoVinculacion;
import com.orbis.api.model.Dispositivo;
import com.orbis.api.repository.CodigoVinculacionRepository;
import com.orbis.api.repository.DispositivoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

/**
 * Identidad por dispositivo (docs/ORBIS_DEVICE_PROTOCOL.md, sección 1; decisión D-002).
 *
 * - Código de vinculación de un solo uso, caduca en 10 minutos, guardado como hash.
 * - Token de 256 bits por dispositivo, devuelto una sola vez, guardado como SHA-256.
 *   Con 256 bits de entropía basta SHA-256 sin sal: no es adivinable por fuerza bruta.
 * - Rotación: el token anterior deja de valer en la misma operación.
 * - Revocación: la siguiente autenticación falla; no hay caché que la retrase.
 */
@Service
public class DispositivoService {

    public static final Duration VALIDEZ_CODIGO = Duration.ofMinutes(10);
    public static final Duration VALIDEZ_TOKEN = Duration.ofDays(180);

    /** Sin caracteres ambiguos (0/O, 1/I/L) para poder leerlo y escribirlo a mano. */
    private static final String ALFABETO = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    public record CodigoGenerado(String codigo, LocalDateTime expiraEn) {
    }

    public record Registro(String deviceId, String token, LocalDateTime tokenValidoHasta) {
    }

    public record Rotacion(String token, LocalDateTime tokenValidoHasta) {
    }

    private final DispositivoRepository dispositivos;
    private final CodigoVinculacionRepository codigos;

    public DispositivoService(DispositivoRepository dispositivos, CodigoVinculacionRepository codigos) {
        this.dispositivos = dispositivos;
        this.codigos = codigos;
    }

    /** Genera un código de vinculación para el usuario. El código en claro solo se devuelve aquí. */
    @Transactional
    public CodigoGenerado crearVinculacion(String propietarioCorreo) {
        String codigo = generarCodigo();
        LocalDateTime expira = LocalDateTime.now().plus(VALIDEZ_CODIGO);
        CodigoVinculacion entidad = new CodigoVinculacion();
        entidad.setCodigoHash(sha256Hex(normalizarCodigo(codigo)));
        entidad.setPropietarioCorreo(propietarioCorreo);
        entidad.setExpiraEn(expira);
        entidad.setUsado(false);
        codigos.save(entidad);
        return new CodigoGenerado(codigo, expira);
    }

    /**
     * Consume el código y registra el dispositivo. Código ausente, usado o caducado
     * producen el mismo error: no revela cuál de las tres cosas ocurrió.
     */
    @Transactional
    public Registro registrar(String codigo, String modeloHardware, String versionFirmware) {
        if (codigo == null || codigo.isBlank()) {
            throw codigoNoValido();
        }
        CodigoVinculacion vinculacion = codigos
                .findByCodigoHash(sha256Hex(normalizarCodigo(codigo)))
                .orElseThrow(DispositivoService::codigoNoValido);
        if (vinculacion.isUsado() || LocalDateTime.now().isAfter(vinculacion.getExpiraEn())) {
            throw codigoNoValido();
        }
        vinculacion.setUsado(true);
        codigos.save(vinculacion);

        String token = generarToken();
        LocalDateTime validoHasta = LocalDateTime.now().plus(VALIDEZ_TOKEN);
        Dispositivo dispositivo = new Dispositivo();
        dispositivo.setId(UUID.randomUUID().toString());
        dispositivo.setPropietarioCorreo(vinculacion.getPropietarioCorreo());
        dispositivo.setTokenHash(sha256Hex(token));
        dispositivo.setEstado(Dispositivo.Estado.ACTIVO);
        dispositivo.setModeloHardware(modeloHardware);
        dispositivo.setVersionFirmware(versionFirmware);
        dispositivo.setCreadoEn(LocalDateTime.now());
        dispositivo.setTokenValidoHasta(validoHasta);
        dispositivos.save(dispositivo);
        return new Registro(dispositivo.getId(), token, validoHasta);
    }

    /** Devuelve el dispositivo si el token es válido: existe, está activo y no ha caducado. */
    @Transactional(readOnly = true)
    public Optional<Dispositivo> autenticar(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        return dispositivos.findByTokenHash(sha256Hex(token))
                .filter(d -> d.getEstado() == Dispositivo.Estado.ACTIVO)
                .filter(d -> LocalDateTime.now().isBefore(d.getTokenValidoHasta()));
    }

    /** Emite un token nuevo y invalida el anterior. Se llama con el dispositivo ya autenticado. */
    @Transactional
    public Rotacion rotar(String deviceId) {
        Dispositivo dispositivo = dispositivos.findById(deviceId)
                .filter(d -> d.getEstado() == Dispositivo.Estado.ACTIVO)
                .orElseThrow(() -> new DispositivoException(HttpStatus.UNAUTHORIZED, "NO_AUTORIZADO"));
        String token = generarToken();
        LocalDateTime validoHasta = LocalDateTime.now().plus(VALIDEZ_TOKEN);
        dispositivo.setTokenHash(sha256Hex(token));
        dispositivo.setTokenValidoHasta(validoHasta);
        dispositivos.save(dispositivo);
        return new Rotacion(token, validoHasta);
    }

    /** Revoca el dispositivo si pertenece al usuario. Devuelve false si no existe o es ajeno. */
    @Transactional
    public boolean revocar(String propietarioCorreo, String deviceId) {
        return dispositivos.findById(deviceId)
                .filter(d -> d.getPropietarioCorreo().equals(propietarioCorreo))
                .map(d -> {
                    d.setEstado(Dispositivo.Estado.REVOCADO);
                    dispositivos.save(d);
                    return true;
                })
                .orElse(false);
    }

    // ── utilidades ─────────────────────────────────────────────────────────

    /** Código con formato XXXX-XXXX. */
    static String generarCodigo() {
        StringBuilder sb = new StringBuilder(9);
        for (int i = 0; i < 8; i++) {
            if (i == 4) {
                sb.append('-');
            }
            sb.append(ALFABETO.charAt(RANDOM.nextInt(ALFABETO.length())));
        }
        return sb.toString();
    }

    /** Mayúsculas y sin separadores ni espacios, para que el código se acepte como lo escribe una persona. */
    static String normalizarCodigo(String codigo) {
        return codigo.trim().toUpperCase().replace("-", "").replace(" ", "");
    }

    static String generarToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String sha256Hex(String texto) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }

    private static DispositivoException codigoNoValido() {
        return new DispositivoException(HttpStatus.CONFLICT, "CODIGO_NO_VALIDO");
    }
}
