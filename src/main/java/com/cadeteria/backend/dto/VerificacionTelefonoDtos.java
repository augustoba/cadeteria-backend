package com.cadeteria.backend.dto;

import jakarta.validation.constraints.NotBlank;

/** Verificación de teléfono para "/pedir" sin login (ver VerificacionTelefonoService). */
public final class VerificacionTelefonoDtos {

    private VerificacionTelefonoDtos() {}

    public record EnviarCodigoRequest(@NotBlank String telefono) {}

    public record VerificarCodigoRequest(@NotBlank String telefono, @NotBlank String codigo) {}

    public record VerificarCodigoResponse(String token) {}

    /**
     * Resultado de pedir el código (spec-antiabuso Fase 2):
     * - CODIGO_WHATSAPP / CODIGO_SMS: se mandó el código por ese medio, token null.
     * - YA_VALIDADO: el teléfono ya pasó la verificación antes, no hace falta código; token listo para usar.
     * - SIN_VERIFICAR: no hay forma de mandar el código ahora; token para enviar igual, la
     *   solicitud entra marcada para que el admin la valide.
     */
    public record EnviarCodigoResponse(String resultado, String token) {}
}
