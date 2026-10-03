package com.cadeteria.backend.repository;

import com.cadeteria.backend.model.CalleNombreAnterior;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CalleNombreAnteriorRepository extends JpaRepository<CalleNombreAnterior, String> {
    List<CalleNombreAnterior> findByNombreNorm(String nombreNorm);
}
