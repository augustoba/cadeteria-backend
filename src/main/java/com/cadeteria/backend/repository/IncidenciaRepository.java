package com.cadeteria.backend.repository;

import com.cadeteria.backend.model.Incidencia;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IncidenciaRepository extends JpaRepository<Incidencia, String> {
    List<Incidencia> findByEstadoOrderByCreadaEnDesc(String estado);

    List<Incidencia> findAllByOrderByCreadaEnDesc();

    /** Incidencias ligadas a un pedido puntual (ronda 7) — para mostrarlas en su detalle. */
    List<Incidencia> findByPedidoIdOrderByCreadaEnDesc(String pedidoId);

    /** Incidencias ligadas a un cadete puntual (ronda 10, punto 108) — para mostrarlas en su ficha. */
    List<Incidencia> findByCadeteIdOrderByCreadaEnDesc(String cadeteId);

    /** Para bloquear la asignación automática a un cadete con un reclamo grave sin resolver. */
    boolean existsByCadeteIdAndPrioridadAndEstado(String cadeteId, String prioridad, String estado);

    /** Para el score de riesgo de la tabla comparativa de cadetes (mejora 2026-09-17). */
    long countByCadeteIdAndEstado(String cadeteId, String estado);

    /** Reclamos de clientes abiertos (ReclamoService, job de seguimiento y cierre). */
    List<Incidencia> findByEstadoAndOrigen(String estado, String origen);

    /** Un cadete con un reclamo de cliente abierto no recibe pedidos (2026-09-26). */
    boolean existsByCadeteIdAndOrigenAndEstado(String cadeteId, String origen, String estado);

    List<Incidencia> findByCadeteIdAndOrigenAndEstado(String cadeteId, String origen, String estado);

    List<Incidencia> findByPedidoIdAndOrigenAndEstado(String pedidoId, String origen, String estado);
}
