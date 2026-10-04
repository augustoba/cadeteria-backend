package com.cadeteria.backend.repository;

import com.cadeteria.backend.model.DireccionAlias;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DireccionAliasRepository extends JpaRepository<DireccionAlias, String> {
    Optional<DireccionAlias> findByVarianteNorm(String varianteNorm);

    /** "colom" -> "colombia", "colombres"... (buscador, 2026-09-28): calles conocidas que empiezan con lo tipeado. */
    List<DireccionAlias> findTop50ByVarianteNormStartingWith(String prefijo);

    /** Todas las formas de escribir una calle (para pasarlas a otro nombre cuando dos calles se unen). */
    List<DireccionAlias> findByCalleCanonica(String calleCanonica);
}
