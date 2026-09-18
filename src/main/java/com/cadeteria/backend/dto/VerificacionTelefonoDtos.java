package com.cadeteria.backend.dto;

import jakarta.validation.constraints.NotBlank;

/** Verificación de teléfono para "/pedir" sin login (ver VerificacionTelefonoService). */
public final class VerificacionTelefonoDtos {

    private VerificacionTelefonoDtos() {}

    public record EnviarCodigoRequest(@NotBlank String telefono) {}

    public record VerificarCodigoRequest(@NotBlank String telefono, @NotBlank String codigo) {}

    public record VerificarCodigoResponse(String token) {}
}
