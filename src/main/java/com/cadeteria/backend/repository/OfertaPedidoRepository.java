package com.cadeteria.backend.repository;

import com.cadeteria.backend.model.OfertaPedido;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OfertaPedidoRepository extends JpaRepository<OfertaPedido, String> {
    Optional<OfertaPedido> findFirstByPedidoIdAndCadeteIdAndResultadoId(
            String pedidoId, String cadeteId, String resultadoId);

    List<OfertaPedido> findByResultadoIdAndExpiraEnBefore(String resultadoId, java.time.Instant instant);

    List<OfertaPedido> findByPedidoId(String pedidoId);

    /** Para las estadísticas de la app (5.5/historial): cuántas ofertas terminaron así para ese cadete. */
    long countByCadeteIdAndResultadoId(String cadeteId, String resultadoId);

    /** Igual, pero acotado a un rango de fechas — para las métricas del panel por cadete. */
    long countByCadeteIdAndResultadoIdAndOfrecidoEnBetween(
            String cadeteId, String resultadoId, java.time.Instant desde, java.time.Instant hasta);

    /** Rechazos con motivo cargado, para la sección "Motivos de rechazo" de Métricas. */
    List<OfertaPedido> findByResultadoIdAndOfrecidoEnBetweenOrderByOfrecidoEnDesc(
            String resultadoId, java.time.Instant desde, java.time.Instant hasta);
}
