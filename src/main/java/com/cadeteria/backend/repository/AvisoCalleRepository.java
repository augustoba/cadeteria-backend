package com.cadeteria.backend.repository;

import com.cadeteria.backend.model.AvisoCalle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface AvisoCalleRepository extends JpaRepository<AvisoCalle, String> {

    /** Activos (no vencidos), los más nuevos primero. */
    List<AvisoCalle> findByVenceEnAfterOrderByCreadoEnDesc(Instant ahora);

    /** Para el tope de avisos por hora de cada cadete. */
    long countByCadeteIdAndCreadoEnAfter(String cadeteId, Instant desde);

    /** Para la ficha del cadete: cuántos avisó y cuántos le bajaron con "ya no está". */
    long countByCadeteId(String cadeteId);

    long countByCadeteIdAndBajadoEnIsNotNull(String cadeteId);
}
