package com.cadeteria.backend.dto;

import jakarta.validation.constraints.NotBlank;

/** "Olvidé mi contraseña" del cadete, desde la app (ver PasswordRecoveryService). */
public final class RecuperarPasswordDtos {

    private RecuperarPasswordDtos() {}

    public record SolicitarRequest(@NotBlank String username) {}

    public record ConfirmarRequest(@NotBlank String username, @NotBlank String codigo, @NotBlank String nuevaPassword) {}
}
