package com.cadeteria.backend.repository;

import com.cadeteria.backend.model.PedidoCadeteExcluido;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PedidoCadeteExcluidoRepository extends JpaRepository<PedidoCadeteExcluido, String> {
    boolean existsByPedidoIdAndCadeteId(String pedidoId, String cadeteId);
}
