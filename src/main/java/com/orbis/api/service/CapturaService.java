package com.orbis.api.service;

import com.orbis.api.model.CapturaFotografia;
import com.orbis.api.model.CapturaImagen;
import com.orbis.api.model.EstadoCaptura;
import com.orbis.api.repository.CapturaFotografiaRepository;
import com.orbis.api.repository.CapturaImagenRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Recepción idempotente de capturas fotográficas y transiciones de su análisis.
 *
 * Reglas:
 * - (propietario, captureId) es único. Un reintento con el mismo contenido devuelve la
 *   captura existente y no crea evento nuevo (duplicada = true).
 * - Mismo captureId con contenido distinto → 409 CAPTURA_CONFLICTO.
 * - Los bytes se guardan en capturas_imagen solo durante el análisis (retención: 24 h máximo).
 */
@Service
public class CapturaService {

    public record Resultado(CapturaFotografia captura, boolean duplicada) {
    }

    private final CapturaFotografiaRepository repository;
    private final CapturaImagenRepository imagenes;
    private final long retencionHoras;

    public CapturaService(CapturaFotografiaRepository repository,
                          CapturaImagenRepository imagenes,
                          @Value("${app.analisis.retencion-horas:24}") long retencionHoras) {
        this.repository = repository;
        this.imagenes = imagenes;
        this.retencionHoras = retencionHoras;
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
            CapturaFotografia guardada = repository.saveAndFlush(nueva);
            CapturaImagen imagen = new CapturaImagen();
            imagen.setCaptura(guardada);
            imagen.setContenido(cuerpo);
            imagen.setCreadaEn(ahora);
            imagenes.saveAndFlush(imagen);
            return new Resultado(guardada, false);
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

    public static final int TAMANO_PAGINA_DEFECTO = 20;
    public static final int TAMANO_PAGINA_MAXIMO = 50;

    /** Capturas del propietario, más recientes primero. page mínimo 0; size por defecto 20 y máximo 50. */
    @Transactional(readOnly = true)
    public Page<CapturaFotografia> listar(String propietarioCorreo, int page, int size) {
        int pagina = Math.max(page, 0);
        int tamano = size < 1 ? TAMANO_PAGINA_DEFECTO : Math.min(size, TAMANO_PAGINA_MAXIMO);
        return repository.findByPropietarioCorreoOrderByRecibidaEnDesc(
                propietarioCorreo, PageRequest.of(pagina, tamano));
    }

    /** Bytes de la captura si aún están dentro de la retención. Nunca devuelve bytes caducados. */
    @Transactional(readOnly = true)
    public Optional<byte[]> contenidoImagenVigente(Long capturaId) {
        LocalDateTime limite = LocalDateTime.now().minusHours(retencionHoras);
        return imagenes.findByCaptura_Id(capturaId)
                .filter(img -> !img.getCreadaEn().isBefore(limite))
                .map(CapturaImagen::getContenido);
    }

    /** Guarda descripción y etiquetas, y pasa la captura de EN_PROCESO a DESCRITA. */
    @Transactional
    public void registrarDescripcion(Long id, String descripcion, String etiquetas) {
        CapturaFotografia captura = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Captura inexistente: " + id));
        captura.setDescripcion(descripcion);
        captura.setEtiquetas(etiquetas);
        transicionar(id, EstadoCaptura.DESCRITA, null);
    }

    /** Borra los bytes de una captura. Idempotente. */
    @Transactional
    public void borrarImagen(Long capturaId) {
        imagenes.deleteByCaptura_Id(capturaId);
    }

    /**
     * Borra los bytes de las capturas más antiguas que la retención. Si la captura seguía en RECIBIDA
     * se marca como FALLIDA (IMAGEN_CADUCADA), para que no quede colgada sin imagen.
     * Devuelve cuántas imágenes se borraron.
     */
    @Transactional
    public int caducarImagenesAntiguas() {
        LocalDateTime limite = LocalDateTime.now().minusHours(retencionHoras);
        List<CapturaImagen> caducadas = imagenes.findByCreadaEnBefore(limite);
        for (CapturaImagen imagen : caducadas) {
            CapturaFotografia captura = imagen.getCaptura();
            if (captura.getEstado() == EstadoCaptura.RECIBIDA) {
                transicionar(captura.getId(), EstadoCaptura.FALLIDA, "IMAGEN_CADUCADA");
            }
            imagenes.delete(imagen);
        }
        return caducadas.size();
    }

    @Transactional(readOnly = true)
    public long contarImagenes(Long capturaId) {
        return imagenes.countByCaptura_Id(capturaId);
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

    /**
     * Cambia el estado de una captura solo si la transición es válida (EstadoCaptura.puedePasarA).
     * Un intento inválido lanza IllegalStateException y no modifica la captura.
     */
    @Transactional
    public CapturaFotografia transicionar(Long id, EstadoCaptura destino, String motivo) {
        CapturaFotografia captura = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Captura inexistente: " + id));
        if (!captura.getEstado().puedePasarA(destino)) {
            throw new IllegalStateException(
                    "Transición no permitida: " + captura.getEstado() + " -> " + destino);
        }
        if (captura.getEstado() != destino) {
            captura.setEstado(destino);
            captura.setActualizadaEn(LocalDateTime.now());
        }
        if (destino == EstadoCaptura.FALLIDA) {
            captura.setMotivoFallo(motivo);
        }
        return repository.save(captura);
    }

    static String sha256Hex(byte[] datos) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(datos));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}
