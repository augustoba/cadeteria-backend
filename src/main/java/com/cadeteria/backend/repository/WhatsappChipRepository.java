package com.cadeteria.backend.repository;

import com.cadeteria.backend.model.WhatsappChip;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WhatsappChipRepository extends JpaRepository<WhatsappChip, String> {
    List<WhatsappChip> findAllByOrderByIdAsc();
}
