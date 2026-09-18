package com.cadeteria.backend.repository;

import com.cadeteria.backend.model.Rol;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RolRepository extends JpaRepository<Rol, String> {
    List<Rol> findAllByOrderByNombreAsc();
}
