package com.cadeteria.backend.repository;

import com.cadeteria.backend.model.WhatsappMensaje;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface WhatsappMensajeRepository extends JpaRepository<WhatsappMensaje, String> {
    /** Para reenviar al gateway apenas reconecta (ver WhatsappGatewayService.reenviarPendientes). */
    List<WhatsappMensaje> findByEstadoOrderByCreadoEnAsc(String estado);

    /** Para la purga por retención (ver RetencionDatosService) — candidatos a borrar. */
    List<WhatsappMensaje> findByCreadoEnBefore(Instant corte);

    /** Panel — listado paginado, filtrable por teléfono (contiene) y rango de fechas (mejora: "son muchísimos"). */
    @Query("SELECT m FROM WhatsappMensaje m WHERE "
            + "(:telefono IS NULL OR :telefono = '' OR m.telefono LIKE CONCAT('%', :telefono, '%')) AND "
            + "(:desde IS NULL OR m.creadoEn >= :desde) AND "
            + "(:hasta IS NULL OR m.creadoEn <= :hasta)")
    Page<WhatsappMensaje> pagina(@Param("telefono") String telefono, @Param("desde") Instant desde,
                                  @Param("hasta") Instant hasta, Pageable pageable);

    /** Para detectar chips "shadowbaneados" — entregan mucho menos que el resto aunque sigan CONECTADO. */
    @Query("SELECT m.chipUsado AS chipId, COUNT(m) AS mandados, "
            + "SUM(CASE WHEN m.entregadoEn IS NOT NULL THEN 1L ELSE 0L END) AS entregados "
            + "FROM WhatsappMensaje m WHERE m.chipUsado IS NOT NULL AND m.estado = 'ENVIADO' AND m.creadoEn >= :desde "
            + "GROUP BY m.chipUsado")
    List<EstadisticaChipRow> estadisticasPorChip(@Param("desde") Instant desde);

    interface EstadisticaChipRow {
        String getChipId();
        long getMandados();
        long getEntregados();
    }
}
