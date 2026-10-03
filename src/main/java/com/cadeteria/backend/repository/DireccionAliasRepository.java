package com.cadeteria.backend.repository;

import com.cadeteria.backend.model.DireccionAlias;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DireccionAliasRepository extends JpaRepository<DireccionAlias, String> {
    Optional<DireccionAlias> findByVarianteNorm(String varianteNorm);

    /** "colom" -> "colombia", "colombres"... (buscador, 2026-09-28): calles conocidas que empiezan con lo tipeado. */
    List<DireccionAlias> findTop50ByVarianteNormStartingWith(String prefijo);

    /** "suipacha" -> "batalla de suipacha" (buscador, 2026-10-03): calles conocidas con lo tipeado dentro del nombre. */
    List<DireccionAlias> findTop50ByVarianteNormContaining(String texto);
}
