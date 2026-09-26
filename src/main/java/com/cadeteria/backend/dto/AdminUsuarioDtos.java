package com.cadeteria.backend.dto;

import com.cadeteria.backend.common.Validaciones;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.Instant;

/** rol es el id de un {@link com.cadeteria.backend.model.Rol} existente — se valida contra la base, no un patrón fijo (roles configurables). */
public final class AdminUsuarioDtos {

    private AdminUsuarioDtos() {}

    public record AdminUsuarioResponse(String id, String username, String rol, boolean enabled, Instant createdAt) {}

    public record CrearAdminRequest(
            @NotBlank(message = "Falta el usuario.") @Pattern(regexp = Validaciones.USUARIO_ADMIN, message = Validaciones.MSJ_USUARIO_ADMIN) String username,
            @NotBlank(message = "Elegí el rol.") String rol) {}

    public record CrearAdminResponse(AdminUsuarioResponse admin, String passwordTemporal) {}

    public record CambiarRolRequest(@NotBlank String rol) {}

    public record ResetearPasswordResponse(String passwordTemporal) {}
}
