package com.cadeteria.backend.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Pedido.zona y Pedido.tipoVehiculoRequerido dejaron de ser obligatorios
 * (documentacion/spec-asignacion-por-distancia.md) — con `ddl-auto: update`, Hibernate
 * agrega columnas nuevas solas (como `requiere_moto`) pero no relaja un NOT NULL que ya
 * existía, así que lo hacemos acá a mano. `tipo_vehiculo_requerido_id` queda como columna
 * huérfana (con el historial viejo, ya no mapeada por ninguna entidad) y se usa una única
 * vez para el backfill de `requiere_moto`: los pedidos nuevos nunca la cargan, así que el
 * backfill es idempotente en cada arranque (nunca toca una fila creada después de este
 * cambio).
 * <p>
 * `MODIFY COLUMN` es sintaxis de MySQL (producción, ver application.yml). Los tests con
 * `@SpringBootTest` (ej. BackendApplicationTests) arrancan contra H2 con un esquema fresco
 * generado directo desde las entidades — ahí no existe nada que migrar (la columna vieja
 * `tipo_vehiculo_requerido_id` ni se crea, al no estar mapeada por ninguna entidad, y
 * `zona_id` ya nace nullable). Cada sentencia se ignora si falla, en vez de tirar abajo el
 * arranque — mismo criterio de degradación que ya usan RutaService/CotizacionService con
 * sus proveedores opcionales.
 */
@Component
@Order(-1)
public class PedidoZonaVehiculoSchemaFix implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(PedidoZonaVehiculoSchemaFix.class);

    private final JdbcTemplate jdbcTemplate;

    public PedidoZonaVehiculoSchemaFix(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        alterSiAplica("ALTER TABLE pedido MODIFY COLUMN zona_id VARCHAR(255) NULL");
        dropForeignKeysSiAplica("pedido", "tipo_vehiculo_requerido_id");
        alterSiAplica("ALTER TABLE pedido MODIFY COLUMN tipo_vehiculo_requerido_id VARCHAR(255) NULL");
        try {
            int actualizados = jdbcTemplate.update(
                    "UPDATE pedido SET requiere_moto = (tipo_vehiculo_requerido_id = 'MOTO') "
                            + "WHERE tipo_vehiculo_requerido_id IS NOT NULL");
            if (actualizados > 0) {
                log.info("Esquema: {} pedido(s) viejo(s) migrado(s) a requiere_moto.", actualizados);
            }
        } catch (DataAccessException e) {
            log.warn("Sin tipo_vehiculo_requerido_id para migrar (esquema nuevo, ej. H2 en tests): {}", e.getMessage());
        }
    }

    private void alterSiAplica(String sql) {
        try {
            jdbcTemplate.execute(sql);
        } catch (DataAccessException e) {
            log.warn("No se aplico '{}' (no hace falta en este entorno/dialecto): {}", sql, e.getMessage());
        }
    }

    /**
     * MySQL no deja hacer MODIFY COLUMN a nullable si la columna sigue participando de una
     * foreign key (error 1832) — y como {@code tipo_vehiculo_requerido_id} queda huérfana (ya
     * no la mapea ninguna entidad), no hace falta que seguir arrastrando esa FK. El nombre de
     * la constraint lo generó Hibernate solo (no es fijo entre entornos), así que se busca en
     * information_schema en vez de hardcodearlo.
     */
    private void dropForeignKeysSiAplica(String tabla, String columna) {
        try {
            List<String> nombres = jdbcTemplate.queryForList(
                    "SELECT CONSTRAINT_NAME FROM information_schema.KEY_COLUMN_USAGE "
                            + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ? "
                            + "AND REFERENCED_TABLE_NAME IS NOT NULL",
                    String.class, tabla, columna);
            for (String nombre : nombres) {
                alterSiAplica("ALTER TABLE " + tabla + " DROP FOREIGN KEY " + nombre);
            }
        } catch (DataAccessException e) {
            log.warn("No se pudieron buscar las FK de {}.{} (no hace falta en este entorno/dialecto): {}",
                    tabla, columna, e.getMessage());
        }
    }
}
