package com.orbis.api.repository;

import com.orbis.api.model.CapturaFotografia;
import org.springframework.data.jpa.repository.JpaRepository;

import com.orbis.api.model.EstadoCaptura;

import java.util.List;
import java.util.Optional;

public interface CapturaFotografiaRepository extends JpaRepository<CapturaFotografia, Long> {

    Optional<CapturaFotografia> findByPropietarioCorreoAndCaptureId(String propietarioCorreo, String captureId);

    List<CapturaFotografia> findByEstado(EstadoCaptura estado);
}
