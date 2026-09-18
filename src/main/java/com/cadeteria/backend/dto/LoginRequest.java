package com.cadeteria.backend.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank String username,
        @NotBlank String password,
        /** Solo lo manda la app de cadetes — para que el panel vea qué versión tiene cada uno (mejora 2026-09-17). */
        Integer versionApp
) {}
