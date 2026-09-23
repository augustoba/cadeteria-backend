package com.cadeteria.backend.repository;

import com.cadeteria.backend.model.Pedido;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PedidoRepository extends JpaRepository<Pedido, String> {
    Optional<Pedido> findByTokenSeguimiento(String tokenSeguimiento);

    List<Pedido> findByEstadoIdInOrderByCreadoEnDesc(List<String> estadoIds);

    List<Pedido> findByCadeteAsignadoIdAndEstadoIdIn(String cadeteId, List<String> estadoIds);

    long countByCadeteAsignadoIdAndEstadoIdIn(String cadeteId, List<String> estadoIds);

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

    /**
     * "Pedidos finalizados" paginado de verdad (mejora 2026-09-16) — antes traía TODO el
     * historial finalizado/cancelado de la cadetería entero a memoria de una sola vez
     * (`findByEstadoIdInOrderByCreadoEnDesc`), y el panel lo recortaba de a 15 en el
     * navegador. Con volumen real (100+ viajes/día) eso iba a envejecer mal — ahora filtra
     * y pagina en la base. `desde`/`hasta` null = sin ese límite (rango "Todo", explícito,
     * no accidental). `cadeteId` null = todos los cadetes.
     */
    @Query("""
            SELECT p FROM Pedido p
            WHERE p.estado.id IN :estadoIds
              AND (:desde IS NULL OR p.creadoEn >= :desde)
              AND (:hasta IS NULL OR p.creadoEn <= :hasta)
              AND (:cadeteId IS NULL OR p.cadeteAsignado.id = :cadeteId)
            ORDER BY p.creadoEn DESC
            """)
    Page<Pedido> paginaFinalizados(@Param("estadoIds") List<String> estadoIds, @Param("desde") Instant desde,
            @Param("hasta") Instant hasta, @Param("cadeteId") String cadeteId, Pageable pageable);

    /** Página del historial de un cliente (antes traía todo y recortaba en Java a 30, ver ClienteService.ficha). */
    List<Pedido> findByClienteTelefonoOrderByCreadoEnDesc(String clienteTelefono, Pageable pageable);

    /**
     * Calificación agrupada por cadete — una sola query para todo el listado del panel. Antes
     * era una query POR cadete (`CadeteService.toResponse`), o sea N queries por carga.
     */
    @Query("""
            select p.cadeteAsignado.id, avg(p.calificacionEstrellas), count(p)
            from Pedido p
            where p.calificacionEstrellas is not null and p.cadeteAsignado is not null
            group by p.cadeteAsignado.id
            """)
    List<Object[]> calificacionPorCadete();

    /**
     * Candidatos para la purga de imágenes (mejora 2026-09-23, RetencionDatosService): solo
     * pedidos ya terminados (nunca uno abierto), con al menos una foto/firma cargada, y cuyo
     * momento de cierre (finalizadoEn o canceladoEn, el que corresponda) es anterior al corte.
     */
    @Query("""
            SELECT p FROM Pedido p
            WHERE p.estado.id IN ('FINALIZADO', 'CANCELADO')
              AND COALESCE(p.finalizadoEn, p.canceladoEn) < :corte
              AND (p.fotoRecepcionUrl IS NOT NULL OR p.entregaFotoUrl IS NOT NULL OR p.firmaReceptorUrl IS NOT NULL)
            """)
    List<Pedido> findConImagenesTerminadosAntesDe(@Param("corte") Instant corte);
}
