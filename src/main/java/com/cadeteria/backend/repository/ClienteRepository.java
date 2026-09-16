package com.cadeteria.backend.repository;

import com.cadeteria.backend.model.Cliente;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClienteRepository extends JpaRepository<Cliente, String> {

    /**
     * Listado de "Clientes" paginado de verdad (mejora 2026-09-16) — antes
     * {@code ClienteService.listar} traía TODA la tabla `pedido` a memoria en cada
     * apertura de la pantalla (`pedidoRepo.findAll()`) solo para agruparla por teléfono en
     * Java. Acá el conteo/suma/último pedido se calculan en la base (agregado SQL), y solo
     * se pagina — el resto de los campos (nombre de contacto, empresa, etc.) se completan
     * después en el service, pero solo para las filas de la página actual, nunca para
     * todas. Un teléfono entra en el listado si tiene al menos un Pedido, o si ya tiene
     * una ficha de Cliente cargada a mano aunque todavía no haya pedido nada (por eso el
     * UNION con la tabla `cliente`).
     */
    @Query(value = """
            SELECT t.telefono AS telefono,
                   COALESCE(pc.cantidad, 0) AS cantidadPedidos,
                   COALESCE(pc.montoTotal, 0) AS montoTotal,
                   pc.ultimoPedidoEn AS ultimoPedidoEn
            FROM (
                SELECT cliente_telefono AS telefono FROM pedido WHERE cliente_telefono IS NOT NULL AND cliente_telefono <> ''
                UNION
                SELECT telefono FROM cliente
            ) t
            LEFT JOIN (
                SELECT cliente_telefono AS telefono, COUNT(*) AS cantidad, SUM(precio) AS montoTotal, MAX(creado_en) AS ultimoPedidoEn
                FROM pedido
                WHERE cliente_telefono IS NOT NULL AND cliente_telefono <> ''
                GROUP BY cliente_telefono
            ) pc ON pc.telefono = t.telefono
            LEFT JOIN cliente c ON c.telefono = t.telefono
            WHERE (:q IS NULL OR :q = '' OR t.telefono LIKE CONCAT('%', :q, '%')
                   OR c.nombre_contacto LIKE CONCAT('%', :q, '%') OR c.empresa LIKE CONCAT('%', :q, '%'))
            ORDER BY (pc.ultimoPedidoEn IS NULL) ASC, pc.ultimoPedidoEn DESC
            """,
            countQuery = """
            SELECT COUNT(*) FROM (
                SELECT cliente_telefono AS telefono FROM pedido WHERE cliente_telefono IS NOT NULL AND cliente_telefono <> ''
                UNION
                SELECT telefono FROM cliente
            ) t
            LEFT JOIN cliente c ON c.telefono = t.telefono
            WHERE (:q IS NULL OR :q = '' OR t.telefono LIKE CONCAT('%', :q, '%')
                   OR c.nombre_contacto LIKE CONCAT('%', :q, '%') OR c.empresa LIKE CONCAT('%', :q, '%'))
            """,
            nativeQuery = true)
    Page<ClienteResumenRow> paginaResumen(@Param("q") String q, Pageable pageable);

    interface ClienteResumenRow {
        String getTelefono();
        Integer getCantidadPedidos();
        java.math.BigDecimal getMontoTotal();
        java.time.Instant getUltimoPedidoEn();
    }
}
