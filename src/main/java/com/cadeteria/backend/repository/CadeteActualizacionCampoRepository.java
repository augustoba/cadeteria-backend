package com.cadeteria.backend.repository;

import com.cadeteria.backend.model.CadeteActualizacionCampo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CadeteActualizacionCampoRepository extends JpaRepository<CadeteActualizacionCampo, String> {

    List<CadeteActualizacionCampo> findByActualizacionId(String actualizacionId);

    @Query("SELECT COUNT(c) > 0 FROM CadeteActualizacionCampo c WHERE c.actualizacion.cadete.id = :cadeteId AND c.estado = :estado")
    boolean existePendientePara(@Param("cadeteId") String cadeteId, @Param("estado") String estado);

    @Query("SELECT c FROM CadeteActualizacionCampo c WHERE c.estado = :estado ORDER BY c.actualizacion.creadoEn DESC")
    List<CadeteActualizacionCampo> findByEstadoOrderByActualizacionCreadoEnDesc(@Param("estado") String estado);
}
