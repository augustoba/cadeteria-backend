package com.cadeteria.backend.dto;

import com.cadeteria.backend.common.Validaciones;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

/** "Olvidé mi contraseña" del cadete, desde la app (ver PasswordRecoveryService). */
public final class RecuperarPasswordDtos {

    private RecuperarPasswordDtos() {}

    public record SolicitarRequest(
            @NotBlank(message = "Falta el DNI.") @Pattern(regexp = Validaciones.DNI, message = Validaciones.MSJ_DNI) String username) {}

    public record ConfirmarRequest(
            @NotBlank(message = "Falta el DNI.") @Pattern(regexp = Validaciones.DNI, message = Validaciones.MSJ_DNI) String username,
            @NotBlank(message = "Falta el código.") @Pattern(regexp = Validaciones.CODIGO_6, message = Validaciones.MSJ_CODIGO) String codigo,
            @NotBlank(message = "Falta la contraseña nueva.")
            @Size(min = Validaciones.PASSWORD_MIN, max = Validaciones.PASSWORD_MAX, message = Validaciones.MSJ_PASSWORD) String nuevaPassword) {}
}
