package com.cadeteria.backend.repository;

import com.cadeteria.backend.model.SolicitudCadete;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SolicitudCadeteRepository extends JpaRepository<SolicitudCadete, String> {
    Optional<SolicitudCadete> findByToken(String token);

    List<SolicitudCadete> findByEstadoOrderByCreadoEnDesc(String estado);

    List<SolicitudCadete> findAllByOrderByCreadoEnDesc();
}
