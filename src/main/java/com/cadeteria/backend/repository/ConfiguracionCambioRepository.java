package com.cadeteria.backend.repository;

import com.cadeteria.backend.model.ConfiguracionCambio;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConfiguracionCambioRepository extends JpaRepository<ConfiguracionCambio, String> {
    List<ConfiguracionCambio> findTop300ByOrderByCambiadoEnDesc();
}
