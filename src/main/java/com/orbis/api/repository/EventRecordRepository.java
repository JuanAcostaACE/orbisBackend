package com.orbis.api.repository;

import com.orbis.api.model.EventRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EventRecordRepository extends JpaRepository<EventRecord, Long> {

    /** Todos los eventos de un usuario, ordenados del más reciente al más antiguo. */
    List<EventRecord> findAllByOwnerIdOrderByCreatedAtDesc(Long userId);

    /** Paginado (P2): mismo filtro con paginación para reducir payload. */
    Page<EventRecord> findAllByOwnerId(Long userId, Pageable pageable);

    /** Lookup seguro: un usuario no puede acceder al evento de otro. */
    Optional<EventRecord> findByIdAndOwnerId(Long id, Long userId);
}
