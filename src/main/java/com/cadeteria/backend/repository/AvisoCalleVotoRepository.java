package com.cadeteria.backend.repository;

import com.cadeteria.backend.model.AvisoCalleVoto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AvisoCalleVotoRepository extends JpaRepository<AvisoCalleVoto, String> {

    Optional<AvisoCalleVoto> findByAvisoIdAndCadeteId(String avisoId, String cadeteId);

    /** Cadetes distintos que votaron eso (hay un voto por cadete y aviso). */
    long countByAvisoIdAndVoto(String avisoId, String voto);

    /** Para la ficha: cuántos avisos de este cadete marcó otro como "ya no está". */
    @Query("select count(distinct v.aviso.id) from AvisoCalleVoto v where v.aviso.cadete.id = :cadeteId and v.voto = 'YA_NO_ESTA'")
    long avisosDelCadeteMarcadosYaNoEsta(@Param("cadeteId") String cadeteId);
}
