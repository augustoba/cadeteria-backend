package com.cadeteria.backend.repository;

import com.cadeteria.backend.model.SolicitudPedido;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SolicitudPedidoRepository extends JpaRepository<SolicitudPedido, String> {
    List<SolicitudPedido> findByEstadoOrderByCreadoEnDesc(String estado);

    List<SolicitudPedido> findAllByOrderByCreadoEnDesc();

    Optional<SolicitudPedido> findByTokenConfirmacion(String tokenConfirmacion);
}
