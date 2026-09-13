package com.cadeteria.backend.repository;

import com.cadeteria.backend.model.Configuracion;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConfiguracionRepository extends JpaRepository<Configuracion, String> {
}
