package com.cadeteria.backend.repository;

import com.cadeteria.backend.model.PagoSemanal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PagoSemanalRepository extends JpaRepository<PagoSemanal, String> {
    List<PagoSemanal> findByCadeteIdOrderBySemanaInicioDesc(String cadeteId);
    Optional<PagoSemanal> findByCadeteIdAndSemanaInicio(String cadeteId, LocalDate semanaInicio);
}
