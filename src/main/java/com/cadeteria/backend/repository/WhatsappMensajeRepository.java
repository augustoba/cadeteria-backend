package com.cadeteria.backend.repository;

import com.cadeteria.backend.model.WhatsappMensaje;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WhatsappMensajeRepository extends JpaRepository<WhatsappMensaje, String> {
    /** Para reenviar al gateway apenas reconecta (ver WhatsappGatewayService.reenviarPendientes). */
    List<WhatsappMensaje> findByEstadoOrderByCreadoEnAsc(String estado);
}
