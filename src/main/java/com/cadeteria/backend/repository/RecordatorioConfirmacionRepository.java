package com.cadeteria.backend.repository;

import com.cadeteria.backend.model.RecordatorioConfirmacion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RecordatorioConfirmacionRepository extends JpaRepository<RecordatorioConfirmacion, String> {

    List<RecordatorioConfirmacion> findTop10ByCadeteIdOrderByConfirmadoEnDesc(String cadeteId);
}
