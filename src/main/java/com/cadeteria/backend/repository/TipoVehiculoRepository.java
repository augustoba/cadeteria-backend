package com.cadeteria.backend.repository;

import com.cadeteria.backend.model.TipoVehiculo;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TipoVehiculoRepository extends JpaRepository<TipoVehiculo, String> {
}
