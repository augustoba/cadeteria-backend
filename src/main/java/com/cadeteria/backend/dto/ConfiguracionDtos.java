package com.cadeteria.backend.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

public final class ConfiguracionDtos {

    private ConfiguracionDtos() {}

    public record ConfiguracionResponse(Map<String, String> valores) {}

    /** valor puede ser vacío (2026-09-26): así el superadmin puede borrar la última API key de una lista. */
    public record ConfiguracionUpdateRequest(@NotBlank String clave, @jakarta.validation.constraints.NotNull String valor) {}

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
            boolean fotoEntregaObligatoria,
            /**
             * "https://.../seguimiento/" (2026-09-25): la app le suma el token del pedido y arma el QR
             * que el cadete le muestra al cliente para que confirme que es el cadete asignado.
             */
            String urlSeguimientoBase,
            /**
             * Retirado / Entregado solo en el lugar (carril B, 2026-09-28): radio alrededor del punto
             * del pedido y error del GPS a partir del cual la ubicación cuenta como imprecisa. El
             * backend vuelve a controlar con los mismos valores.
             */
            int enLugarRadioM,
            int enLugarPrecisionMaxM,
            /** Interruptor de Configuración: apagado, la app no frena por distancia ni GPS (solo exige el orden). */
            boolean enLugarControlActivo,
            /** Cartel "Antes de arrancar" (2026-09-29): se edita en Configuración; sin textos o apagado no sale. */
            boolean recordatoriosActivo,
            String recordatoriosTitulo,
            java.util.List<String> recordatoriosTextos,
            /** Minutos mínimos entre Retirado y Finalizar (2026-09-29); 0 = sin espera. El backend vuelve a controlar. */
            int minutosMinimosRetiroEntrega
    ) {}
}
