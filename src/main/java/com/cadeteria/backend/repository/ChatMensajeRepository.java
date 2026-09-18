package com.cadeteria.backend.repository;

import com.cadeteria.backend.model.ChatMensaje;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface ChatMensajeRepository extends JpaRepository<ChatMensaje, String> {
    List<ChatMensaje> findByCadeteIdOrderByEnviadoEnAsc(String cadeteId);

    /** Para la purga por retención (ver RetencionDatosService) — candidatos a borrar. */
    List<ChatMensaje> findByEnviadoEnBefore(Instant corte);

    /** Para el badge de "mensajes sin leer" del panel — mensajes de cadetes que el admin todavia no vio. */
    long countByAutorIdAndLeidoFalse(String autorId);

    /** Para saber CUÁL cadete tiene mensajes sin leer, no solo cuántos en total (auditoría UX 2026-09-13). */
    List<ChatMensaje> findByAutorIdAndLeidoFalse(String autorId);
}
