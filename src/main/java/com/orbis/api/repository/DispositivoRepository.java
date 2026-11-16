package com.orbis.api.repository;

import com.orbis.api.model.Dispositivo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DispositivoRepository extends JpaRepository<Dispositivo, String> {

    Optional<Dispositivo> findByTokenHash(String tokenHash);

    List<Dispositivo> findByPropietarioCorreo(String propietarioCorreo);
}
