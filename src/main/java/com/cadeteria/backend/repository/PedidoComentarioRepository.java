package com.cadeteria.backend.repository;

import com.cadeteria.backend.model.PedidoComentario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PedidoComentarioRepository extends JpaRepository<PedidoComentario, String> {
    List<PedidoComentario> findByPedidoIdOrderByCreadoEnAsc(String pedidoId);
}
