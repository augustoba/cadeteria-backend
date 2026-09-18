package com.cadeteria.backend.repository;

import com.cadeteria.backend.model.Permiso;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PermisoRepository extends JpaRepository<Permiso, String> {
    List<Permiso> findAllByOrderByCategoriaAscNombreAsc();
}
