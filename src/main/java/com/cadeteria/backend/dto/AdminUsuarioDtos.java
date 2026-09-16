package com.cadeteria.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;

public final class AdminUsuarioDtos {

    private AdminUsuarioDtos() {}

    public record AdminUsuarioResponse(String id, String username, String rol, boolean enabled, Instant createdAt) {}

    public record CrearAdminRequest(
            @NotBlank String username,
            @Pattern(regexp = "DUENO|OPERADOR", message = "rol debe ser DUENO u OPERADOR") @NotBlank String rol) {}

    public record CrearAdminResponse(AdminUsuarioResponse admin, String passwordTemporal) {}

    public record CambiarRolRequest(@Pattern(regexp = "DUENO|OPERADOR", message = "rol debe ser DUENO u OPERADOR") @NotBlank String rol) {}

    public record ResetearPasswordResponse(String passwordTemporal) {}
}
