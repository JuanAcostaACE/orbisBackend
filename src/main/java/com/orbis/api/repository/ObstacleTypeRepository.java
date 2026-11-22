package com.orbis.api.repository;

import com.orbis.api.model.ObstacleType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ObstacleTypeRepository extends JpaRepository<ObstacleType, Long> {
}