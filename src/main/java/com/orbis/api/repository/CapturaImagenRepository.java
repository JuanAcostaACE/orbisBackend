package com.orbis.api.repository;

import com.orbis.api.model.CapturaImagen;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CapturaImagenRepository extends JpaRepository<CapturaImagen, Long> {

    Optional<CapturaImagen> findByCaptura_Id(Long capturaId);

    long countByCaptura_Id(Long capturaId);

    List<CapturaImagen> findByCreadaEnBefore(LocalDateTime limite);

    void deleteByCaptura_Id(Long capturaId);
}
