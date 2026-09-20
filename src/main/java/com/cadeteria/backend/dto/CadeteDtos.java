package com.cadeteria.backend.dto;

import com.cadeteria.backend.model.Cadete;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;

public final class CadeteDtos {

    private CadeteDtos() {}

    /** El usuario de login del cadete es su DNI: solo dígitos, hasta 8 (nada de letras ni más largo). */
    static final String REGEX_USERNAME_DNI = "^[0-9]{1,8}$";
    static final String MENSAJE_USERNAME_DNI = "El usuario debe ser el DNI: solo números, sin puntos ni letras, máximo 8 dígitos";

    /** password es obligatoria solo al crear (el service la ignora si viene null en un update). */
    public record CadeteRequest(
            @NotBlank String nombre,
            @NotBlank String apellido,
            @NotBlank String dni,
            @NotBlank String telefono,
            String email,
            String fotoUrl,
            @NotBlank String tipoVehiculoId,
            String vehiculoColor,
            String vehiculoPatente,
            String vehiculoMarca,
            String vehiculoModelo,
            Integer vehiculoAnio,
            String fotoVehiculoUrl,
            String fotoCarnetUrl,
            String fotoTarjetaVerdeUrl,
            @NotBlank @Pattern(regexp = REGEX_USERNAME_DNI, message = MENSAJE_USERNAME_DNI) String username,
            String password,
            BigDecimal montoMaximoTransportado,
            Integer maxViajesSimultaneos,
            /** null = sin tope — máximo de viajes FINALIZADOS por día/semana (ronda 6, punto 33). */
            Integer maxViajesDiarios,
            Integer maxViajesSemanales,
            /** Ambos null = sin turno fijo, disponible siempre (comportamiento previo). */
            LocalTime turnoInicio,
            LocalTime turnoFin,
            /** "SEMANAL" | "PORCENTAJE" — null/blank se toma como "SEMANAL" (ronda 7). */
            String modalidadPago,
            /** Notas libres del admin sobre este cadete (ronda 10, punto 103). */
            String notasInternas
    ) {}

    public record CadeteResponse(
            String id, String nombre, String apellido, String dni, String telefono, String email, String fotoUrl,
            LookupResponse tipoVehiculo, String vehiculoColor, String vehiculoPatente,
            String vehiculoMarca, String vehiculoModelo, Integer vehiculoAnio, String fotoVehiculoUrl,
            String fotoCarnetUrl, String fotoTarjetaVerdeUrl,
            String username, boolean activo, LookupResponse estado,
            Double lat, Double lng, Instant ubicacionActualizadaEn, LookupResponse zonaActual,
            BigDecimal montoMaximoTransportado, Integer maxViajesSimultaneos,
            Integer maxViajesDiarios, Integer maxViajesSemanales,
            /** Cuándo quedó libre por última vez — define el orden FIFO de la cola (diseno-tecnico.md sección 5). */
            Instant ordenColaEspera,
            /** Datos de cobro por transferencia — los carga el propio cadete desde la app. */
            String cbu, String aliasCbu,
            /** Ambos null = sin turno fijo (disponible siempre para la sugerencia automática). */
            LocalTime turnoInicio, LocalTime turnoFin,
            /** Calificación histórica (todo el registro, no acotada a un rango) — null si todavía no lo calificó nadie. */
            Double calificacionPromedio, long calificacionCantidad,
            /** Modelo de cobro (ronda 7): "SEMANAL" o "PORCENTAJE". */
            String modalidadPago,
            /** Solo aplica con modalidadPago=SEMANAL. */
            boolean habilitadoPago, BigDecimal pagoSemanalMontoPagado, Instant pagoSemanalVenceEn,
            /** Precio de la cuota semanal de este cadete — null si nunca se cargó (se usa el monto global de Configuración). */
            BigDecimal montoSemanalActual,
            /** Solo aplica con modalidadPago=PORCENTAJE. */
            BigDecimal creditoDisponible,
            /** Notas libres del admin sobre este cadete (ronda 10, punto 103). */
            String notasInternas,
            /** Última versión de APK con la que se logueó, y cuándo — null si nunca lo reportó (mejora 2026-09-17). */
            Integer ultimaVersionApp, Instant ultimaVersionAppEn
    ) {
        /** Conveniencia para los endpoints que no recalculan la calificación (una mutación puntual, no la lista). */
        public static CadeteResponse from(Cadete c) {
            return from(c, null, 0);
        }

        public static CadeteResponse from(Cadete c, Double calificacionPromedio, long calificacionCantidad) {
            return new CadeteResponse(
                    c.getId(), c.getNombre(), c.getApellido(), c.getDni(), c.getTelefono(), c.getEmail(), c.getFotoUrl(),
                    LookupResponse.from(c.getTipoVehiculo()), c.getVehiculoColor(), c.getVehiculoPatente(),
                    c.getVehiculoMarca(), c.getVehiculoModelo(), c.getVehiculoAnio(), c.getFotoVehiculoUrl(),
                    c.getFotoCarnetUrl(), c.getFotoTarjetaVerdeUrl(),
                    c.getUsername(), c.isActivo(), LookupResponse.from(c.getEstado()),
                    c.getLat(), c.getLng(), c.getUbicacionActualizadaEn(), LookupResponse.from(c.getZonaActual()),
                    c.getMontoMaximoTransportado(), c.getMaxViajesSimultaneos(),
                    c.getMaxViajesDiarios(), c.getMaxViajesSemanales(), c.getOrdenColaEspera(),
                    c.getCbu(), c.getAliasCbu(), c.getTurnoInicio(), c.getTurnoFin(),
                    calificacionPromedio, calificacionCantidad,
                    c.getModalidadPago(), c.isHabilitadoPago(), c.getPagoSemanalMontoPagado(), c.getPagoSemanalVenceEn(),
                    c.getMontoSemanalActual(),
                    c.getCreditoDisponible(), c.getNotasInternas(),
                    c.getUltimaVersionApp(), c.getUltimaVersionAppEn());
        }

        /**
         * Para el listado del panel: el listado no muestra fotos ni datos de cobro, así que no
         * tienen por qué viajar en cada carga. Mismo criterio que {@code PedidoResponse.fromResumen()},
         * que saca las paradas del listado de pedidos.
         *
         * Para la ficha ({@code GET /api/cadetes/{id}}) y el perfil propio
         * ({@code GET /api/cadetes/me}) se sigue usando {@link #from}, que manda todo — ahí las
         * fotos y el CBU sí se muestran.
         */
        public static CadeteResponse fromListado(Cadete c, Double calificacionPromedio, long calificacionCantidad) {
            CadeteResponse r = from(c, calificacionPromedio, calificacionCantidad);
            return new CadeteResponse(
                    r.id(), r.nombre(), r.apellido(), r.dni(), r.telefono(), r.email(),
                    null,                                    // fotoUrl
                    r.tipoVehiculo(), r.vehiculoColor(), r.vehiculoPatente(),
                    r.vehiculoMarca(), r.vehiculoModelo(), r.vehiculoAnio(),
                    null,                                    // fotoVehiculoUrl
                    null,                                    // fotoCarnetUrl
                    null,                                    // fotoTarjetaVerdeUrl
                    r.username(), r.activo(), r.estado(),
                    r.lat(), r.lng(), r.ubicacionActualizadaEn(), r.zonaActual(),
                    r.montoMaximoTransportado(), r.maxViajesSimultaneos(),
                    r.maxViajesDiarios(), r.maxViajesSemanales(), r.ordenColaEspera(),
                    null,                                    // cbu
                    null,                                    // aliasCbu
                    r.turnoInicio(), r.turnoFin(),
                    r.calificacionPromedio(), r.calificacionCantidad(),
                    r.modalidadPago(), r.habilitadoPago(), r.pagoSemanalMontoPagado(), r.pagoSemanalVenceEn(),
                    r.montoSemanalActual(),
                    r.creditoDisponible(), r.notasInternas(),
                    r.ultimaVersionApp(), r.ultimaVersionAppEn());
        }
    }

    /**
     * Habilitar (o re-habilitar) a un cadete SEMANAL, con pago completo o parcial (ronda 7).
     * montoSemanal es opcional — si viene, se guarda como el nuevo precio de la cuota semanal
     * de este cadete (pantalla "Pagos" unificada).
     */
    public record HabilitarPagoSemanalRequest(@jakarta.validation.constraints.NotNull BigDecimal montoPagado, Instant venceEn,
                                               BigDecimal montoSemanal) {}

    /** Cargar crédito a un cadete PORCENTAJE tras recibir su transferencia (ronda 7). */
    public record AcreditarRequest(@jakarta.validation.constraints.NotNull BigDecimal monto) {}

    /** Cambiar el modelo de cobro de un cadete desde la pantalla "Pagos" (antes solo se podía al crear/editar el cadete). */
    public record ModalidadPagoRequest(@NotBlank String modalidadPago) {}

    /** El admin la ve por si no le llegó el mail al cadete (mejora 2026-09-17). */
    public record ReenviarPasswordResponse(String passwordTemporal) {}

    /** motivo: por qué se dio de baja/reactivó (ronda 10, punto 96) — opcional. */
    public record ActivoRequest(boolean activo, String motivo) {}

    public record CadeteEstadoLogResponse(String id, boolean activo, String motivo, Instant cambiadoEn, String cambiadoPorUsername) {}

    /**
     * Panorama completo del cadete para su ficha en el panel: datos + estadísticas
     * históricas (todo el registro, no acotadas a un rango como en Métricas), sus
     * incidencias (con link al pedido si están ligadas a uno) y el historial de
     * altas/bajas.
     */
    public record CadeteFichaResponse(
            CadeteResponse cadete,
            MetricasDtos.CadeteMetricaResponse estadisticas,
            java.util.List<IncidenciaDtos.IncidenciaResponse> incidencias,
            java.util.List<CadeteEstadoLogResponse> historialEstado
    ) {}

    public record MovimientoCreditoResponse(
            String id, String tipo, BigDecimal monto, BigDecimal saldoResultante,
            Long pedidoNumero, Instant creadoEn, String creadoPorUsername
    ) {}

    public record EstadoRequest(@NotBlank String estadoId) {}

    public record UbicacionRequest(double lat, double lng) {}

    public record FcmTokenRequest(@NotBlank String fcmToken) {}

    /** El cadete cambia su propia contraseña desde la app (spec: se le puede romper el celular). */
    public record CambiarPasswordRequest(@NotBlank String actual, @NotBlank String nueva) {}

    /** El cadete cambia su propio teléfono desde la app (si cambia de celular/línea). */
    public record TelefonoRequest(@NotBlank String telefono) {}

    /** El cadete carga sus propios datos de cobro (para que el cliente le transfiera). */
    public record CuentaRequest(String cbu, String aliasCbu) {}

    /** Aviso general del admin a todos los cadetes conectados ahora mismo (spec: "cerramos temprano", etc). */
    public record AvisoGeneralRequest(@NotBlank String mensaje) {}

    /** Para el panel: cuántos de los cadetes que recibieron el aviso ya lo confirmaron. */
    public record AvisoGeneralResponse(String id, String mensaje, Instant enviadoEn, int totalDestinatarios, long totalLeido) {
        public static AvisoGeneralResponse from(com.cadeteria.backend.model.AvisoGeneral a, long totalLeido) {
            return new AvisoGeneralResponse(a.getId(), a.getMensaje(), a.getEnviadoEn(), a.getTotalDestinatarios(), totalLeido);
        }
    }

    /** Pantalla "Avisos" con historial de la app (mejora 2026-09-16) — a diferencia de {@link AvisoGeneralResponse}, incluye los ya leídos y de quién los mandó no hace falta (siempre es el admin). */
    public record AvisoGeneralHistorialResponse(String id, String mensaje, Instant enviadoEn, boolean leidoPorMi) {
        public static AvisoGeneralHistorialResponse from(com.cadeteria.backend.model.AvisoGeneral a, boolean leidoPorMi) {
            return new AvisoGeneralHistorialResponse(a.getId(), a.getMensaje(), a.getEnviadoEn(), leidoPorMi);
        }
    }
}
