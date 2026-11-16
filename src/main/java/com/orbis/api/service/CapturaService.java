package com.orbis.api.service;

import com.orbis.api.model.CapturaFotografia;
import com.orbis.api.model.EstadoCaptura;
import com.orbis.api.repository.CapturaFotografiaRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

/**
 * Recepción idempotente de capturas fotográficas.
 *
 * Reglas:
 * - (propietario, captureId) es único. Un reintento con el mismo contenido devuelve la
 *   captura existente y no crea evento nuevo (duplicada = true).
 * - Mismo captureId con contenido distinto → 409 CAPTURA_CONFLICTO.
 */
@Service
public class CapturaService {

    public record Resultado(CapturaFotografia captura, boolean duplicada) {
    }

    private final CapturaFotografiaRepository repository;

    public CapturaService(CapturaFotografiaRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public Resultado recibir(String propietarioCorreo, String captureId, byte[] cuerpo) {
        validarCaptureId(captureId);
        ValidadorJpeg.validarCuerpo(cuerpo);
        String sha256 = sha256Hex(cuerpo);

        Optional<CapturaFotografia> existente = repository
                .findByPropietarioCorreoAndCaptureId(propietarioCorreo, captureId);
        if (existente.isPresent()) {
            return resolverDuplicado(existente.get(), sha256);
        }

        CapturaFotografia nueva = new CapturaFotografia();
        nueva.setCaptureId(captureId);
        nueva.setPropietarioCorreo(propietarioCorreo);
        nueva.setSha256(sha256);
        nueva.setTamanoBytes(cuerpo.length);
        nueva.setEstado(EstadoCaptura.RECIBIDA);
        LocalDateTime ahora = LocalDateTime.now();
        nueva.setRecibidaEn(ahora);
        nueva.setActualizadaEn(ahora);
        try {
            return new Resultado(repository.saveAndFlush(nueva), false);
        } catch (DataIntegrityViolationException carrera) {
            // Otro reintento concurrente insertó la misma captura entre la consulta y el insert.
            CapturaFotografia ganadora = repository
                    .findByPropietarioCorreoAndCaptureId(propietarioCorreo, captureId)
                    .orElseThrow(() -> carrera);
            return resolverDuplicado(ganadora, sha256);
        }
    }

    @Transactional(readOnly = true)
    public Optional<CapturaFotografia> obtener(String propietarioCorreo, String captureId) {
        return repository.findByPropietarioCorreoAndCaptureId(propietarioCorreo, captureId);
    }

    private Resultado resolverDuplicado(CapturaFotografia existente, String sha256) {
        if (!existente.getSha256().equals(sha256)) {
            throw new CapturaException(HttpStatus.CONFLICT, "CAPTURA_CONFLICTO");
        }
        return new Resultado(existente, true);
    }

    private static void validarCaptureId(String captureId) {
        if (captureId == null) {
            throw new CapturaException(HttpStatus.BAD_REQUEST, "ID_CAPTURA_REQUERIDO");
        }
        try {
            UUID.fromString(captureId);
        } catch (IllegalArgumentException e) {
            throw new CapturaException(HttpStatus.BAD_REQUEST, "ID_CAPTURA_INVALIDO");
        }
        if (captureId.length() != 36) {
            throw new CapturaException(HttpStatus.BAD_REQUEST, "ID_CAPTURA_INVALIDO");
        }
    }

    static String sha256Hex(byte[] datos) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(datos));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}
