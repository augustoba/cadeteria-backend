package com.cadeteria.backend.repository;

import com.cadeteria.backend.model.ReporteCliente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;

import java.util.Collection;
import java.util.List;

public interface ReporteClienteRepository extends JpaRepository<ReporteCliente, String> {

    List<ReporteCliente> findByTelefonoOrderByCreadoEnDesc(String telefono);

    /** Para resolver los avisos de toda una página de solicitudes en una sola query (sin N+1). */
    List<ReporteCliente> findByTelefonoInOrderByCreadoEnDesc(Collection<String> telefonos);

    boolean existsByPedidoIdAndCadeteIdAndTipo(String pedidoId, String cadeteId, String tipo);

    @Modifying
    @Query("update ReporteCliente r set r.telefono = :nuevo where r.telefono = :viejo")
    int cambiarTelefono(@Param("viejo") String viejo, @Param("nuevo") String nuevo);
}
