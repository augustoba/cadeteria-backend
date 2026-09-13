package com.cadeteria.backend.dto;

import com.cadeteria.backend.model.AccesoLog;

import java.time.Instant;

public final class SeguridadDtos {

    private SeguridadDtos() {}

    public record AccesoLogResponse(String id, String username, Instant ingresoEn, String ip) {
        public static AccesoLogResponse from(AccesoLog a) {
            return new AccesoLogResponse(a.getId(), a.getUsername(), a.getIngresoEn(), a.getIp());
        }
    }

    public record CerrarSesionesResponse(int sesionesInvalidadas) {}
}
