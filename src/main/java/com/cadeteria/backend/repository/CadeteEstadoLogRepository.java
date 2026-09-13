package com.cadeteria.backend.repository;

import com.cadeteria.backend.model.CadeteEstadoLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CadeteEstadoLogRepository extends JpaRepository<CadeteEstadoLog, String> {
    List<CadeteEstadoLog> findByCadeteIdOrderByCambiadoEnDesc(String cadeteId);
}
