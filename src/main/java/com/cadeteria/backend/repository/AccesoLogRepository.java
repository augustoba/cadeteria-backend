package com.cadeteria.backend.repository;

import com.cadeteria.backend.model.AccesoLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccesoLogRepository extends JpaRepository<AccesoLog, String> {
    Page<AccesoLog> findAllByOrderByIngresoEnDesc(Pageable pageable);
}
