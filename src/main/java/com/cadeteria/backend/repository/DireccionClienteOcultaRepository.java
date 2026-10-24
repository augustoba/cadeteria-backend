package com.cadeteria.backend.repository;

import com.cadeteria.backend.model.DireccionClienteOculta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;

import java.util.List;
import java.util.Optional;

public interface DireccionClienteOcultaRepository extends JpaRepository<DireccionClienteOculta, String> {
    List<DireccionClienteOculta> findByTelefono(String telefono);

    Optional<DireccionClienteOculta> findByTelefonoAndClave(String telefono, String clave);

    @Modifying
    @Query("update DireccionClienteOculta d set d.telefono = :nuevo where d.telefono = :viejo")
    int cambiarTelefono(@Param("viejo") String viejo, @Param("nuevo") String nuevo);
}
