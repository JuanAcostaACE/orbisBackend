package com.orbis.api.repository;

import com.orbis.api.model.CodigoVinculacion;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface CodigoVinculacionRepository extends JpaRepository<CodigoVinculacion, Long> {

    /** Bloqueo pesimista: dos registros simultáneos con el mismo código no pueden consumirlo a la vez. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<CodigoVinculacion> findByCodigoHash(String codigoHash);
}
