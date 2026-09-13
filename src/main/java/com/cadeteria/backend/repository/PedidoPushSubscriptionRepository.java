package com.cadeteria.backend.repository;

import com.cadeteria.backend.model.PedidoPushSubscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PedidoPushSubscriptionRepository extends JpaRepository<PedidoPushSubscription, String> {
    List<PedidoPushSubscription> findByPedidoId(String pedidoId);

    boolean existsByPedidoIdAndEndpoint(String pedidoId, String endpoint);
}
