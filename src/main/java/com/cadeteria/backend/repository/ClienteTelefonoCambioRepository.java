package com.cadeteria.backend.repository;

import com.cadeteria.backend.model.ClienteTelefonoCambio;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ClienteTelefonoCambioRepository extends JpaRepository<ClienteTelefonoCambio, String> {
    /** Los cambios donde el número fue el anterior o el nuevo, para mostrarlos en la ficha. */
    List<ClienteTelefonoCambio> findByTelefonoNuevoOrTelefonoAnteriorOrderByCambiadoEnDesc(String telefonoNuevo, String telefonoAnterior);
}
