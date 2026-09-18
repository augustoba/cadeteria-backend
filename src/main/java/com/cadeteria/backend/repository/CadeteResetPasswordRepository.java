package com.cadeteria.backend.repository;

import com.cadeteria.backend.model.CadeteResetPassword;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CadeteResetPasswordRepository extends JpaRepository<CadeteResetPassword, String> {
    /** El código vigente más reciente sin usar de ese cadete (si pidió varios, solo el último cuenta). */
    Optional<CadeteResetPassword> findTopByCadeteIdAndUsadoEnIsNullOrderByCreadoEnDesc(String cadeteId);
}
