package com.cadeteria.backend.repository;

import com.cadeteria.backend.model.DireccionAlias;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DireccionAliasRepository extends JpaRepository<DireccionAlias, String> {
    Optional<DireccionAlias> findByVarianteNorm(String varianteNorm);
}
