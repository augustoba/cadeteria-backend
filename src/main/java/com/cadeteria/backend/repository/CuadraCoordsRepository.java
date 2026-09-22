package com.cadeteria.backend.repository;

import com.cadeteria.backend.model.CuadraCoords;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CuadraCoordsRepository extends JpaRepository<CuadraCoords, String> {

    /**
     * Todas las localidades que tienen esta calle+cuadra cacheada. Se usa para el LOOKUP, donde
     * todavía no se sabe la localidad (el cliente no la tipeó): si da más de una fila, la calle
     * es ambigua entre pueblos y no se puede resolver en silencio (ver DireccionCacheService).
     */
    List<CuadraCoords> findByCalleCanonicaAndCuadra(String calleCanonica, int cuadra);

    /** Para el GUARDADO, donde la localidad ya la devolvió el geocoder junto con el resultado. */
    Optional<CuadraCoords> findByCalleCanonicaAndLocalidadAndCuadra(String calleCanonica, String localidad, int cuadra);
}
