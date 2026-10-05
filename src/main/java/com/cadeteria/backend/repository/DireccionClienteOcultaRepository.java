package com.cadeteria.backend.repository;

import com.cadeteria.backend.model.DireccionClienteOculta;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DireccionClienteOcultaRepository extends JpaRepository<DireccionClienteOculta, String> {
    List<DireccionClienteOculta> findByTelefono(String telefono);

    Optional<DireccionClienteOculta> findByTelefonoAndClave(String telefono, String clave);
}
