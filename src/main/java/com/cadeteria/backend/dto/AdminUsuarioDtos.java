package com.cadeteria.backend.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;

/** rol es el id de un {@link com.cadeteria.backend.model.Rol} existente — se valida contra la base, no un patrón fijo (roles configurables). */
public final class AdminUsuarioDtos {

    private AdminUsuarioDtos() {}

    public record AdminUsuarioResponse(String id, String username, String rol, boolean enabled, Instant createdAt) {}

    public record CrearAdminRequest(@NotBlank String username, @NotBlank String rol) {}

    public record CrearAdminResponse(AdminUsuarioResponse admin, String passwordTemporal) {}

    public record CambiarRolRequest(@NotBlank String rol) {}

    public record ResetearPasswordResponse(String passwordTemporal) {}
}
