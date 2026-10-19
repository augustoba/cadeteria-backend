package com.cadeteria.backend.repository;

import com.cadeteria.backend.model.PagoSemanalMovimiento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface PagoSemanalMovimientoRepository extends JpaRepository<PagoSemanalMovimiento, String> {
    List<PagoSemanalMovimiento> findByCadeteIdAndSemanaInicioOrderByCreadoEnDesc(String cadeteId, LocalDate semanaInicio);
}
