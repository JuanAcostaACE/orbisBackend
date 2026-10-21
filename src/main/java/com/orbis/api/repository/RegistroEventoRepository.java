package com.orbis.api.repository;

import com.orbis.api.model.RegistroEvento;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RegistroEventoRepository extends JpaRepository<RegistroEvento, Long> {

    /** Todos los eventos de un usuario, ordenados del más reciente al más antiguo. */
    List<RegistroEvento> findAllByPropietarioIdOrderByFechaHoraDesc(Long usuarioId);

    /** Paginado (P2): mismo filtro con paginación para reducir payload. */
    Page<RegistroEvento> findAllByPropietarioId(Long usuarioId, Pageable pageable);

    /** Lookup seguro: un usuario no puede acceder al evento de otro. */
    Optional<RegistroEvento> findByIdAndPropietarioId(Long id, Long usuarioId);
}