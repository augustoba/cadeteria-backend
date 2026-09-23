package com.cadeteria.backend.repository;

import com.cadeteria.backend.model.CadeteActualizacion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CadeteActualizacionRepository extends JpaRepository<CadeteActualizacion, String> {
    List<CadeteActualizacion> findByCadeteIdOrderByCreadoEnDesc(String cadeteId);
}
