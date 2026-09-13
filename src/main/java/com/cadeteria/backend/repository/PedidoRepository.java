package com.cadeteria.backend.repository;

import com.cadeteria.backend.model.Pedido;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PedidoRepository extends JpaRepository<Pedido, String> {
    Optional<Pedido> findByTokenSeguimiento(String tokenSeguimiento);

    List<Pedido> findByEstadoIdInOrderByCreadoEnDesc(List<String> estadoIds);

    List<Pedido> findByCadeteAsignadoIdAndEstadoIdIn(String cadeteId, List<String> estadoIds);

    List<Pedido> findByCadeteAsignadoIdAndEstadoIdOrderByFinalizadoEnDesc(String cadeteId, String estadoId);

    /** Para métricas por cadete en un rango de fechas (finalizadoEn cae en [desde, hasta)). */
    List<Pedido> findByCadeteAsignadoIdAndEstadoIdAndFinalizadoEnBetweenOrderByFinalizadoEnDesc(
            String cadeteId, String estadoId, Instant desde, Instant hasta);

    List<Pedido> findByEstadoIdAndFechaProgramadaLessThanEqual(String estadoId, Instant instante);

    /** Para el resumen de métricas del día: todos los pedidos creados en [desde, hasta). */
    List<Pedido> findByCreadoEnBetween(Instant desde, Instant hasta);

    /** Autocompletar nombre por teléfono al cargar un pedido (spec 5.6) — el más reciente para ese número. */
    Optional<Pedido> findFirstByClienteTelefonoOrderByCreadoEnDesc(String clienteTelefono);

    /** Historial completo de un cliente para su ficha (ronda 4, punto 44). */
    List<Pedido> findByClienteTelefonoOrderByCreadoEnDesc(String clienteTelefono);

    /** Para el registro de calificaciones del cadete (histórico completo, no acotado a un rango). */
    List<Pedido> findByCadeteAsignadoIdAndCalificacionEstrellasIsNotNull(String cadeteId);

    /** Para el ícono de alertas centralizado del panel (ronda 4, punto 18). */
    long countByEstadoIdInAndSmsFallidoTrue(List<String> estadoIds);

    /** Latido de vida del sistema para el panel de salud (mejora 48) — último pedido creado, sin importar el estado. */
    Optional<Pedido> findFirstByOrderByCreadoEnDesc();
}
