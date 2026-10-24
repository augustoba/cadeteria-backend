package com.cadeteria.backend.repository;

import com.cadeteria.backend.model.Cadete;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface CadeteRepository extends JpaRepository<Cadete, String> {
    Optional<Cadete> findByUsername(String username);
    Optional<Cadete> findByDni(String dni);

    /** Lee el cadete bloqueando la fila (SELECT ... FOR UPDATE) para tocar su crédito sin pisar otra operación. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Cadete c where c.id = :id")
    Optional<Cadete> findByIdParaActualizar(@Param("id") String id);

    /** Solo las marcas del aviso de crédito bajo: la revisión periódica no tiene que pisar el saldo si justo cambió. */
    @Modifying
    @org.springframework.transaction.annotation.Transactional
    @Query("update Cadete c set c.alertaCreditoBajoEnviada = :enviada, c.ultimoAvisoCreditoBajoEn = :en where c.id = :id")
    void guardarAvisoCreditoBajo(@Param("id") String id, @Param("enviada") boolean enviada, @Param("en") Instant en);
}
