package com.cadeteria.backend.repository;

import com.cadeteria.backend.model.VerificacionTelefono;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface VerificacionTelefonoRepository extends JpaRepository<VerificacionTelefono, String> {
    /** El código vigente más reciente sin verificar de ese teléfono (si pidió varios, solo el último cuenta). */
    Optional<VerificacionTelefono> findTopByTelefonoAndVerificadoEnIsNullOrderByCreadoEnDesc(String telefono);

    Optional<VerificacionTelefono> findByToken(String token);
}
