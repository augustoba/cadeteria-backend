package com.cadeteria.backend.repository;

import com.cadeteria.backend.model.AvisoGeneral;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AvisoGeneralRepository extends JpaRepository<AvisoGeneral, String> {
    List<AvisoGeneral> findTop20ByOrderByEnviadoEnDesc();
}
