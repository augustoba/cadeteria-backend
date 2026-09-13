package com.cadeteria.backend.repository;

import com.cadeteria.backend.model.AvisoGeneralLectura;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AvisoGeneralLecturaRepository extends JpaRepository<AvisoGeneralLectura, String> {
    long countByAvisoId(String avisoId);

    /** Para saber qué avisos ya vio un cadete puntual (ronda 10, punto 98). */
    @org.springframework.data.jpa.repository.Query("select l.aviso.id from AvisoGeneralLectura l where l.cadete.id = :cadeteId")
    java.util.List<String> avisoIdsVistosPor(String cadeteId);

    Optional<AvisoGeneralLectura> findByAvisoIdAndCadeteId(String avisoId, String cadeteId);
}
