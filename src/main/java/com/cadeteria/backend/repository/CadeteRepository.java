package com.cadeteria.backend.repository;

import com.cadeteria.backend.model.Cadete;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CadeteRepository extends JpaRepository<Cadete, String> {
    Optional<Cadete> findByUsername(String username);
    Optional<Cadete> findByDni(String dni);
}
