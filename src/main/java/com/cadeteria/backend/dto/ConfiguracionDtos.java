package com.cadeteria.backend.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

public final class ConfiguracionDtos {

    private ConfiguracionDtos() {}

    public record ConfiguracionResponse(Map<String, String> valores) {}

    public record ConfiguracionUpdateRequest(@NotBlank String clave, @NotBlank String valor) {}

    /** Subconjunto de configuración que necesita la app de cadetes (el resto es solo para el panel admin). */
    public record CadeteConfigResponse(
            int frecuenciaUbicacionSeg, String cloudinaryCloudName, String cloudinaryUploadPreset,
            /** Código de versión mínimo requerido — dado el modelo de distribución por Bluetooth (sin
             * Play Store), un cadete puede quedar con una versión vieja sin enterarse. */
            int versionMinimaApp,
            /** Si hace falta la firma digital del receptor para poder finalizar (ronda 3, punto 51). */
            boolean firmaReceptorObligatoria,
            /** Modelo de cobro del cadete (ronda 7) — para que la app calcule "cuánto falta" y muestre alertas. */
            java.math.BigDecimal pagoSemanalMonto,
            java.math.BigDecimal comisionPorcentaje,
            java.math.BigDecimal creditoBajoAlertaUmbral,
            /** Para la cuenta regresiva real al ofrecer un viaje nuevo (auditoría UX 2026-09-13). */
            int tiempoLimiteAceptacionSeg,
            /** Teléfono de contacto de la cadetería para la pantalla de Ayuda de la app (mejora 2026-09-16). */
            String telefonoSoporte,
            /** Si hace falta tener carnet/tarjeta verde/foto del vehículo cargados para poder activarse (mejora 2026-09-16). */
            boolean checklistDocumentacionObligatorio,
            /** Fotos configurables (spec mejoras visuales §6): la app pide la foto ANTES de intentar, en vez de esperar el error. */
            boolean fotoRetiroObligatoria,
            boolean fotoEntregaObligatoria
    ) {}
}
