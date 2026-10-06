package com.orbis.api.repository;

import com.orbis.api.model.RegistroEvento;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegistroEventoRepository extends JpaRepository<RegistroEvento, Long> {
}