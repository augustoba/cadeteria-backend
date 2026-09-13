package com.cadeteria.backend.dto;

import com.cadeteria.backend.model.Incidencia;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;

/** Incidencias/tickets generales del admin (ronda 4, punto 63). */
public final class IncidenciaDtos {

    private IncidenciaDtos() {}

    /**
     * pedidoId opcional (ronda 7): si se crea desde el detalle de un pedido puntual.
     * cadeteId opcional (ronda 10, punto 108): si es sobre un cadete puntual — permite
     * bloquear la asignación automática si queda GRAVE y ABIERTA.
     * prioridad: "BAJA" | "NORMAL" | "GRAVE" — null/blank se toma como "NORMAL".
     */
    public record IncidenciaRequest(@NotBlank String titulo, String descripcion, String pedidoId, String cadeteId, String prioridad) {}

    public record IncidenciaResponse(
            String id, String titulo, String descripcion, String estado, String prioridad,
            Instant creadaEn, String creadaPorUsername, Instant cerradaEn, String cerradaPorUsername,
            String pedidoId, Long pedidoNumero, String cadeteId, String cadeteNombre
    ) {
        public static IncidenciaResponse from(Incidencia i) {
            return new IncidenciaResponse(
                    i.getId(), i.getTitulo(), i.getDescripcion(), i.getEstado(), i.getPrioridad(),
                    i.getCreadaEn(), i.getCreadaPorUsername(), i.getCerradaEn(), i.getCerradaPorUsername(),
                    i.getPedidoId(), i.getPedidoNumero(), i.getCadeteId(), i.getCadeteNombre());
        }
    }
}
