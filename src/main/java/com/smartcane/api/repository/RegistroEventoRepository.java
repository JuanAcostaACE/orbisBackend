package com.smartcane.api.repository;

import com.smartcane.api.model.RegistroEvento;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegistroEventoRepository extends JpaRepository<RegistroEvento, Long> {
}