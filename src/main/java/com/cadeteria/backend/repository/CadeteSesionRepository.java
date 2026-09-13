package com.cadeteria.backend.repository;

import com.cadeteria.backend.model.CadeteSesion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface CadeteSesionRepository extends JpaRepository<CadeteSesion, String> {

    Optional<CadeteSesion> findFirstByCadeteIdAndDesconectadoEnIsNull(String cadeteId);

    /** Sesiones de un cadete que se solapan con [desde, hasta) — para sumar horas online en el rango. */
    @Query("select s from CadeteSesion s where s.cadete.id = :cadeteId and s.conectadoEn < :hasta "
            + "and (s.desconectadoEn is null or s.desconectadoEn > :desde)")
    List<CadeteSesion> findSolapadas(@Param("cadeteId") String cadeteId, @Param("desde") Instant desde, @Param("hasta") Instant hasta);
}
