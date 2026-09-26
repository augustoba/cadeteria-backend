package com.cadeteria.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "Falta el usuario.") @Size(max = 100, message = "Usuario demasiado largo.") String username,
        @NotBlank(message = "Falta la contraseña.") @Size(max = 100, message = "Contraseña demasiado larga.") String password,
        /** Solo lo manda la app de cadetes — para que el panel vea qué versión tiene cada uno (mejora 2026-09-17). */
        Integer versionApp
) {}
