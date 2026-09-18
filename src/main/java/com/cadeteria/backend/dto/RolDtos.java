package com.cadeteria.backend.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

/** Roles con permisos configurables (ver RolService). */
public final class RolDtos {

    private RolDtos() {}

    public record PermisoResponse(String id, String nombre, String categoria) {}

    public record RolRequest(@NotBlank String nombre, List<String> permisos) {}

    public record RolResponse(String id, String nombre, boolean esSistema, List<PermisoResponse> permisos) {}
}
