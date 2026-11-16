package com.orbis.api.repository;

import com.orbis.api.model.CapturaFotografia;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CapturaFotografiaRepository extends JpaRepository<CapturaFotografia, Long> {

    Optional<CapturaFotografia> findByPropietarioCorreoAndCaptureId(String propietarioCorreo, String captureId);
}
