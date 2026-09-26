package com.cadeteria.backend.dto;

import com.cadeteria.backend.model.SolicitudCadete;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Formulario de alta de cadete por link propio de un solo uso (ronda 7). */
public final class SolicitudCadeteDtos {

    private SolicitudCadeteDtos() {}

    public record GenerarLinkResponse(String token, String url) {}

    /**
     * Para la página pública: si el link sigue siendo válido antes de mostrar el formulario. Si el
     * admin pidió correcciones, {@code correccion} trae lo ya cargado para precargar el formulario.
     */
    public record TokenEstadoResponse(boolean valido, String motivo, CorreccionResponse correccion) {}

    /** Un dato o foto marcado mal: {@code campo} es la clave (ej. "fotoCarnetUrl"), {@code etiqueta} lo que ve el postulante. */
    public record ObservacionResponse(String campo, String etiqueta, String motivo) {}

    /** Lo que ya había cargado el postulante; las fotos a corregir vienen en null (tiene que subir otra). */
    public record CorreccionResponse(
            String nombre, String apellido, String dni, String telefono, String email, String tipoVehiculoId,
            String vehiculoColor, String vehiculoPatente, String vehiculoMarca, String vehiculoModelo,
            String fotoUrl, String fotoVehiculoUrl, String fotoCarnetUrl, String fotoCarnetDorsoUrl,
            String fotoTarjetaVerdeUrl, String fotoTarjetaVerdeDorsoUrl,
            List<ObservacionResponse> observaciones) {}

    /**
     * Ya hay (o hubo) un cadete con ese DNI — puede ser alguien que quiere volver. Con la última
     * baja y su motivo para que el admin decida sin tener que ir a buscarlo.
     */
    public record CadeteExistenteResponse(String id, String nombre, String apellido, String username, boolean activo,
                                          String motivoUltimaBaja, Instant fechaUltimaBaja) {}

    /** El admin marca qué corregir: campo -> motivo (ver SolicitudCadeteService.CAMPOS_REVISABLES). */
    public record PedirCorreccionRequest(Map<String, String> observaciones) {}

    /** mailEnviado=false: el mail no está configurado o no hay email — el admin le pasa el link a mano. */
    public record ReenviarLinkResponse(String url, Instant expiraEn, boolean mailEnviado) {}

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
            String fotoCarnetDorsoUrl,
            String fotoTarjetaVerdeUrl,
            /** Ya no se pide "usuario" (2026-09-25): el usuario es el DNI, ver SolicitudCadeteService. */
            String fotoTarjetaVerdeDorsoUrl
    ) {}

    public record SolicitudResponse(
            String id, String token, String estado, Instant creadoEn, Instant expiraEn, Instant enviadaEn,
            String nombre, String apellido, String dni, String telefono, String email,
            LookupResponse tipoVehiculo, String vehiculoColor, String vehiculoPatente,
            String vehiculoMarca, String vehiculoModelo,
            String fotoUrl, String fotoVehiculoUrl, String fotoCarnetUrl, String fotoCarnetDorsoUrl,
            String fotoTarjetaVerdeUrl, String fotoTarjetaVerdeDorsoUrl,
            String usernamePropuesto, String motivoRechazo, String cadeteCreadoId,
            List<ObservacionResponse> observaciones, int correcciones,
            /** null = ese DNI nunca estuvo registrado como cadete. */
            CadeteExistenteResponse cadeteExistente
    ) {
        public static SolicitudResponse from(SolicitudCadete s) {
            return from(s, null);
        }

        public static SolicitudResponse from(SolicitudCadete s, CadeteExistenteResponse cadeteExistente) {
            return new SolicitudResponse(
                    s.getId(), s.getToken(), s.getEstado(), s.getCreadoEn(), s.getExpiraEn(), s.getEnviadaEn(),
                    s.getNombre(), s.getApellido(), s.getDni(), s.getTelefono(), s.getEmail(),
                    LookupResponse.from(s.getTipoVehiculo()), s.getVehiculoColor(), s.getVehiculoPatente(),
                    s.getVehiculoMarca(), s.getVehiculoModelo(),
                    s.getFotoUrl(), s.getFotoVehiculoUrl(), s.getFotoCarnetUrl(), s.getFotoCarnetDorsoUrl(),
                    s.getFotoTarjetaVerdeUrl(), s.getFotoTarjetaVerdeDorsoUrl(),
                    s.getUsernamePropuesto(), s.getMotivoRechazo(), s.getCadeteCreadoId(),
                    com.cadeteria.backend.service.SolicitudCadeteService.observacionesDe(s),
                    s.getCorrecciones() == null ? 0 : s.getCorrecciones(),
                    cadeteExistente);
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
