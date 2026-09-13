package com.cadeteria.backend.repository;

import com.cadeteria.backend.model.EstadoCadete;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EstadoCadeteRepository extends JpaRepository<EstadoCadete, String> {
}
