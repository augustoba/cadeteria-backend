package com.cadeteria.backend.repository;

import com.cadeteria.backend.model.MovimientoCredito;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MovimientoCreditoRepository extends JpaRepository<MovimientoCredito, String> {
    List<MovimientoCredito> findByCadeteIdOrderByCreadoEnDesc(String cadeteId);
}
