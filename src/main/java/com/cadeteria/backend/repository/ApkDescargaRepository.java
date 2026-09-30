package com.cadeteria.backend.repository;

import com.cadeteria.backend.model.ApkDescarga;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ApkDescargaRepository extends JpaRepository<ApkDescarga, String> {

    /** El último link generado para un cadete, para mostrar en su ficha si lo descargó. */
    Optional<ApkDescarga> findFirstByCadeteIdOrderByCreadoEnDesc(String cadeteId);
}
