package com.cadeteria.backend.repository;

import com.cadeteria.backend.model.WhatsappRespuesta;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface WhatsappRespuestaRepository extends JpaRepository<WhatsappRespuesta, String> {
    /** Panel — listado paginado, filtrable por teléfono (contiene) y rango de fechas. */
    @Query("SELECT r FROM WhatsappRespuesta r WHERE "
            + "(:telefono IS NULL OR :telefono = '' OR r.telefono LIKE CONCAT('%', :telefono, '%')) AND "
            + "(:desde IS NULL OR r.recibidoEn >= :desde) AND "
            + "(:hasta IS NULL OR r.recibidoEn <= :hasta)")
    Page<WhatsappRespuesta> pagina(@Param("telefono") String telefono, @Param("desde") Instant desde,
                                    @Param("hasta") Instant hasta, Pageable pageable);
}
