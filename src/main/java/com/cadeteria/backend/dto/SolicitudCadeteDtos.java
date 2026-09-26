package com.cadeteria.backend.dto;

import com.cadeteria.backend.model.SolicitudCadete;
import com.cadeteria.backend.common.Validaciones;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

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
    public record PedirCorreccionRequest(
            @NotEmpty(message = "Marcá al menos un dato para corregir.") Map<String, @Size(max = 300) String> observaciones) {}

    /** mailEnviado=false: el mail no está configurado o no hay email — el admin le pasa el link a mano. */
    public record ReenviarLinkResponse(String url, Instant expiraEn, boolean mailEnviado) {}

    public record SolicitudFormRequest(
            @NotBlank(message = "Falta el nombre.") @Size(max = 60, message = "El nombre puede tener hasta 60 letras.")
            @Pattern(regexp = Validaciones.NOMBRE_PERSONA, message = Validaciones.MSJ_NOMBRE) String nombre,
            @NotBlank(message = "Falta el apellido.") @Size(max = 60, message = "El apellido puede tener hasta 60 letras.")
            @Pattern(regexp = Validaciones.NOMBRE_PERSONA, message = Validaciones.MSJ_APELLIDO) String apellido,
            @NotBlank(message = "Falta el DNI.") @Pattern(regexp = Validaciones.DNI, message = Validaciones.MSJ_DNI) String dni,
            @NotBlank(message = "Falta el teléfono.") @Pattern(regexp = Validaciones.TELEFONO, message = Validaciones.MSJ_TELEFONO) String telefono,
            @NotBlank(message = "Falta el email.") @Size(max = 120, message = "El email puede tener hasta 120 caracteres.")
            @Pattern(regexp = Validaciones.EMAIL, message = Validaciones.MSJ_EMAIL) String email,
            @NotBlank(message = "Elegí el tipo de vehículo.") String tipoVehiculoId,
            @Pattern(regexp = Validaciones.VACIO_O + Validaciones.COLOR, message = Validaciones.MSJ_COLOR) String vehiculoColor,
            /** Obligatoria para moto (lo valida el service); 123ABC o A123BCD. */
            @Pattern(regexp = Validaciones.VACIO_O + Validaciones.PATENTE_MOTO, message = Validaciones.MSJ_PATENTE) String vehiculoPatente,
            @Pattern(regexp = Validaciones.VACIO_O + Validaciones.MARCA_MODELO, message = Validaciones.MSJ_MARCA) String vehiculoMarca,
            @Pattern(regexp = Validaciones.VACIO_O + Validaciones.MARCA_MODELO, message = Validaciones.MSJ_MODELO) String vehiculoModelo,
            @Size(max = 500) String fotoUrl,
            @Size(max = 500) String fotoVehiculoUrl,
            @Size(max = 500) String fotoCarnetUrl,
            @Size(max = 500) String fotoCarnetDorsoUrl,
            @Size(max = 500) String fotoTarjetaVerdeUrl,
            /** Ya no se pide "usuario" (2026-09-25): el usuario es el DNI, ver SolicitudCadeteService. */
            @Size(max = 500) String fotoTarjetaVerdeDorsoUrl,
            /** Tildó "Soy mayor de 18 años" (2026-09-26): sin esto no se acepta el formulario. */
            @AssertTrue(message = "Tenés que ser mayor de 18 años para anotarte como cadete.") boolean mayorDeEdad
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
            CadeteExistenteResponse cadeteExistente,
            /** Cuándo tildó "Soy mayor de 18 años" (2026-09-26); null en solicitudes anteriores a esa casilla. */
            Instant mayorEdadDeclaradaEn
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
                    cadeteExistente,
                    s.getMayorEdadDeclaradaEn());
        }
    }

    /** El admin puede cambiar el usuario propuesto antes de crear la cuenta. */
    /** modalidadPago: "SEMANAL" | "PORCENTAJE" — la elige el admin al aprobar, null/blank cae en "SEMANAL". */
    public record AprobarRequest(
            @NotBlank @Pattern(regexp = CadeteDtos.REGEX_USERNAME_DNI, message = CadeteDtos.MENSAJE_USERNAME_DNI) String username,
            String modalidadPago) {}

    /** passwordTemporal: por si el mail no está configurado todavía, el admin se la puede pasar a mano. */
    public record AprobarResponse(SolicitudResponse solicitud, String username, String passwordTemporal) {}

    public record RechazarRequest(@Size(max = 255, message = "El motivo puede tener hasta 255 caracteres.") String motivo) {}
}
