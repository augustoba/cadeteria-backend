package com.cadeteria.backend.repository;

import com.cadeteria.backend.model.PedidoUbicacion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PedidoUbicacionRepository extends JpaRepository<PedidoUbicacion, String> {
    List<PedidoUbicacion> findByPedidoIdOrderByCapturadoEnAsc(String pedidoId);
}
