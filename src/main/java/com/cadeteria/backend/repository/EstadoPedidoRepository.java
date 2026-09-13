package com.cadeteria.backend.repository;

import com.cadeteria.backend.model.EstadoPedido;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EstadoPedidoRepository extends JpaRepository<EstadoPedido, String> {
}
