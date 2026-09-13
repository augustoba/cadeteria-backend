package com.cadeteria.backend.repository;

import com.cadeteria.backend.model.PedidoParada;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PedidoParadaRepository extends JpaRepository<PedidoParada, String> {
    List<PedidoParada> findByPedidoIdOrderByOrdenAsc(String pedidoId);
}
