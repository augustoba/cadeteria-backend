package com.cadeteria.backend.repository;

import com.cadeteria.backend.model.CuadraCoords;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CuadraCoordsRepository extends JpaRepository<CuadraCoords, String> {

    /**
     * Todas las localidades que tienen esta calle+cuadra cacheada. Se usa para el LOOKUP, donde
     * todavía no se sabe la localidad (el cliente no la tipeó): si da más de una fila, la calle
     * es ambigua entre pueblos y no se puede resolver en silencio (ver DireccionCacheService).
     */
    List<CuadraCoords> findByCalleCanonicaAndCuadra(String calleCanonica, int cuadra);

    /** Todas las cuadras conocidas de una calle (para estimar una altura entre dos cuadras aprendidas, 2026-10-03). */
    List<CuadraCoords> findByCalleCanonica(String calleCanonica);

    /** Para el GUARDADO, donde la localidad ya la devolvió el geocoder junto con el resultado. */
    Optional<CuadraCoords> findByCalleCanonicaAndLocalidadAndCuadra(String calleCanonica, String localidad, int cuadra);

    /** Filas dentro de un rectángulo (el punto aprendido más cercano a un pin o a un cadete, 2026-09-28). */
    List<CuadraCoords> findByLatBetweenAndLngBetween(double latMin, double latMax, double lngMin, double lngMax);

    /** Purga de las ubicaciones de Google vencidas (ver DireccionCacheService#borrarVencidas). */
    long deleteByProveedorInAndCreadaEnBefore(Collection<String> proveedores, Instant limite);
}
