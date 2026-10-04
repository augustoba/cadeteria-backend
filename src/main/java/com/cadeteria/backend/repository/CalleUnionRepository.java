package com.cadeteria.backend.repository;

import com.cadeteria.backend.model.CalleUnion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CalleUnionRepository extends JpaRepository<CalleUnion, String> {
    List<CalleUnion> findTop100ByOrderByCuandoDesc();
}
