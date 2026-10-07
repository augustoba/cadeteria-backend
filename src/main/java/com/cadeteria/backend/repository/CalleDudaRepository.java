package com.cadeteria.backend.repository;

import com.cadeteria.backend.model.CalleDuda;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CalleDudaRepository extends JpaRepository<CalleDuda, String> {

    List<CalleDuda> findByEstado(String estado);
}
