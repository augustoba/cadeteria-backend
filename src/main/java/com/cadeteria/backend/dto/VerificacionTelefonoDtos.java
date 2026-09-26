package com.cadeteria.backend.dto;

import com.cadeteria.backend.common.Validaciones;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

/** Verificación de teléfono para "/pedir" sin login (ver VerificacionTelefonoService). */
public final class VerificacionTelefonoDtos {

    private VerificacionTelefonoDtos() {}

    public record EnviarCodigoRequest(
            @NotBlank(message = "Falta el teléfono.") @Pattern(regexp = Validaciones.TELEFONO, message = Validaciones.MSJ_TELEFONO) String telefono) {}

    public record VerificarCodigoRequest(
            @NotBlank(message = "Falta el teléfono.") @Pattern(regexp = Validaciones.TELEFONO, message = Validaciones.MSJ_TELEFONO) String telefono,
            @NotBlank(message = "Falta el código.") @Pattern(regexp = Validaciones.CODIGO_6, message = Validaciones.MSJ_CODIGO) String codigo) {}

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
