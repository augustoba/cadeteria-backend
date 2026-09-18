package com.cadeteria.backend.dto;

import com.cadeteria.backend.model.SolicitudCadete;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;

/** Formulario de alta de cadete por link propio de un solo uso (ronda 7). */
public final class SolicitudCadeteDtos {

    private SolicitudCadeteDtos() {}

    public record GenerarLinkResponse(String token, String url) {}

    /** Para la página pública: si el link sigue siendo válido antes de mostrar el formulario. */
    public record TokenEstadoResponse(boolean valido, String motivo) {}

    public record SolicitudFormRequest(
            @NotBlank String nombre,
            @NotBlank String apellido,
            @NotBlank String dni,
            @NotBlank String telefono,
            @NotBlank String email,
            @NotBlank String tipoVehiculoId,
            String vehiculoColor,
            String vehiculoPatente,
            String vehiculoMarca,
            String vehiculoModelo,
            String fotoUrl,
            String fotoVehiculoUrl,
            String fotoCarnetUrl,
            String fotoTarjetaVerdeUrl,
            @NotBlank @Pattern(regexp = CadeteDtos.REGEX_USERNAME_DNI, message = CadeteDtos.MENSAJE_USERNAME_DNI) String usernamePropuesto
    ) {}

    public record SolicitudResponse(
            String id, String token, String estado, Instant creadoEn, Instant expiraEn, Instant enviadaEn,
            String nombre, String apellido, String dni, String telefono, String email,
            LookupResponse tipoVehiculo, String vehiculoColor, String vehiculoPatente,
            String vehiculoMarca, String vehiculoModelo,
            String fotoUrl, String fotoVehiculoUrl, String fotoCarnetUrl, String fotoTarjetaVerdeUrl,
            String usernamePropuesto, String motivoRechazo, String cadeteCreadoId
    ) {
        public static SolicitudResponse from(SolicitudCadete s) {
            return new SolicitudResponse(
                    s.getId(), s.getToken(), s.getEstado(), s.getCreadoEn(), s.getExpiraEn(), s.getEnviadaEn(),
                    s.getNombre(), s.getApellido(), s.getDni(), s.getTelefono(), s.getEmail(),
                    LookupResponse.from(s.getTipoVehiculo()), s.getVehiculoColor(), s.getVehiculoPatente(),
                    s.getVehiculoMarca(), s.getVehiculoModelo(),
                    s.getFotoUrl(), s.getFotoVehiculoUrl(), s.getFotoCarnetUrl(), s.getFotoTarjetaVerdeUrl(),
                    s.getUsernamePropuesto(), s.getMotivoRechazo(), s.getCadeteCreadoId());
        }
    }

    /** El admin puede cambiar el usuario propuesto antes de crear la cuenta. */
    /** modalidadPago: "SEMANAL" | "PORCENTAJE" — la elige el admin al aprobar, null/blank cae en "SEMANAL". */
    public record AprobarRequest(
            @NotBlank @Pattern(regexp = CadeteDtos.REGEX_USERNAME_DNI, message = CadeteDtos.MENSAJE_USERNAME_DNI) String username,
            String modalidadPago) {}

    /** passwordTemporal: por si el mail no está configurado todavía, el admin se la puede pasar a mano. */
    public record AprobarResponse(SolicitudResponse solicitud, String username, String passwordTemporal) {}

    public record RechazarRequest(String motivo) {}
}
