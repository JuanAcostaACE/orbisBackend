package com.orbis.api.repository;

import com.orbis.api.model.CapturaFotografia;
import org.springframework.data.jpa.repository.JpaRepository;

import com.orbis.api.model.EstadoCaptura;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface CapturaFotografiaRepository extends JpaRepository<CapturaFotografia, Long> {

    Optional<CapturaFotografia> findByPropietarioCorreoAndCaptureId(String propietarioCorreo, String captureId);

    Page<CapturaFotografia> findByPropietarioCorreoOrderByRecibidaEnDesc(String propietarioCorreo, Pageable pageable);

    List<CapturaFotografia> findByEstado(EstadoCaptura estado);
}
