package com.cadeteria.backend.repository;

import com.cadeteria.backend.model.PedidoPrecioLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PedidoPrecioLogRepository extends JpaRepository<PedidoPrecioLog, String> {
    List<PedidoPrecioLog> findByPedidoIdOrderByCambiadoEnDesc(String pedidoId);
}
