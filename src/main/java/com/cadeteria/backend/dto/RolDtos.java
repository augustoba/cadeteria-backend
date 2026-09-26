package com.cadeteria.backend.dto;

import com.cadeteria.backend.common.Validaciones;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.List;

/** Roles con permisos configurables (ver RolService). */
public final class RolDtos {

    private RolDtos() {}

    public record PermisoResponse(String id, String nombre, String categoria) {}

    public record RolRequest(
            @NotBlank(message = "Falta el nombre del rol.") @Size(max = 50, message = "El nombre del rol puede tener hasta 50 caracteres.")
            String nombre,
            List<String> permisos) {}

    public record RolResponse(String id, String nombre, boolean esSistema, List<PermisoResponse> permisos) {}
}
