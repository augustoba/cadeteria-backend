package com.cadeteria.backend.repository;

import com.cadeteria.backend.model.ReporteCliente;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ReporteClienteRepository extends JpaRepository<ReporteCliente, String> {

    List<ReporteCliente> findByTelefonoOrderByCreadoEnDesc(String telefono);

    /** Para resolver los avisos de toda una página de solicitudes en una sola query (sin N+1). */
    List<ReporteCliente> findByTelefonoInOrderByCreadoEnDesc(Collection<String> telefonos);

    boolean existsByPedidoIdAndCadeteIdAndTipo(String pedidoId, String cadeteId, String tipo);
}
