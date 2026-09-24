package com.cadeteria.backend.service;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

/**
 * Sugiere un precio de viaje sin que el admin tenga que tipearlo, a partir de origen y destino.
 * <p>
 * **Solo por distancia** (2026-09-24 — la cadetería ya no trabaja por zonas, ni para asignar ni
 * para cobrar): se calcula la distancia real por calle (via {@link RutaService}, perfil moto) — o
 * en línea recta (Haversine) × {@code factor_linea_recta} (default 1,4) si el servicio falla, para
 * no romper la sugerencia por eso — la línea recta sola cobraba de menos: medido en 12 viajes de
 * Tucumán (2026-09-24), la distancia por calle es en promedio 1,38 veces la recta. Sobre
 * esa distancia se cobra {@code precio_base_viaje} (el mínimo, default $2000) hasta los primeros
 * {@code distancia_minima_km} (default 2), y {@code precio_por_km} (default $320) por cada km
 * adicional — no desde el km 0, esos primeros km ya los cubre el mínimo. Todo configurable desde
 * Configuración → Tarifas. Con {@code precio_por_km} en 0 no hay sugerencia — el precio se carga
 * a mano como siempre, esto es una ayuda, nunca bloquea el flujo.
 * <p>
 * Antes había un modo por zona (tarifa fija de la zona del origen/destino) que en la práctica
 * hacía que todo viaje saliera con la tarifa de la zona demo ($700) — se sacó.
 * <p>
 * Encima se suma un recargo por el dinero que el cliente declara transportar (riesgo del
 * cadete): cada {@code recargo_dinero_transportado_umbral} pesos declarados suma
 * {@code recargo_dinero_transportado_monto}, redondeando el tramo incompleto hacia abajo (ej. con
 * umbral 10000 y monto 100, declarar $25000 suma $200). Umbral en 0 desactiva el recargo.
 */
@Service
public class CotizacionService {

    private final ConfiguracionService configuracionService;
    private final RutaService rutaService;

    public CotizacionService(ConfiguracionService configuracionService, RutaService rutaService) {
        this.configuracionService = configuracionService;
        this.rutaService = rutaService;
    }

    /**
     * zonaId/zonaNombre quedan siempre en null (compatibilidad con el front). metodo: "DISTANCIA" (por
     * calle) o "DISTANCIA_ESTIMADA" (falló el ruteo: línea recta × factor_linea_recta).
     */
    public record Cotizacion(BigDecimal precioSugerido, String metodo, String zonaId, String zonaNombre, Double distanciaKm) {}

    public Optional<Cotizacion> cotizar(double origenLat, double origenLng, Double destinoLat, Double destinoLng,
                                         BigDecimal montoDeclarado) {
        if (destinoLat == null || destinoLng == null) {
            return Optional.empty();
        }
        if (configuracionService.getBigDecimal("precio_por_km", BigDecimal.ZERO).signum() <= 0) {
            return Optional.empty();
        }
        Optional<RutaService.Resumen> ruta = rutaService.resumenSiDisponible(origenLat, origenLng, destinoLat, destinoLng, "MOTO");
        double km;
        String metodo;
        if (ruta.isPresent()) {
            km = ruta.get().distanciaM() / 1000.0;
            metodo = "DISTANCIA";
        } else {
            BigDecimal factor = configuracionService.getBigDecimal("factor_linea_recta", new BigDecimal("1.4"));
            km = GeocodingService.distanciaKm(origenLat, origenLng, destinoLat, destinoLng) * factor.doubleValue();
            metodo = "DISTANCIA_ESTIMADA";
        }
        return Optional.of(new Cotizacion(precioParaKm(km, montoDeclarado), metodo, null, null, km));
    }

    /** Tarifa actual aplicada a una distancia: mínimo hasta distancia_minima_km + precio_por_km por km extra + recargo. */
    public BigDecimal precioParaKm(double km, BigDecimal montoDeclarado) {
        BigDecimal precioPorKm = configuracionService.getBigDecimal("precio_por_km", BigDecimal.ZERO);
        BigDecimal precioBase = configuracionService.getBigDecimal("precio_base_viaje", BigDecimal.ZERO);
        BigDecimal distanciaMinimaKm = configuracionService.getBigDecimal("distancia_minima_km", BigDecimal.valueOf(2));
        double kmAdicionales = Math.max(0, km - distanciaMinimaKm.doubleValue());
        return precioBase.add(precioPorKm.multiply(BigDecimal.valueOf(kmAdicionales)))
                .add(recargoPorDinero(montoDeclarado))
                .setScale(0, RoundingMode.HALF_UP);
    }

    /**
     * Recargo por el dinero/valores que el cliente declara transportar (mayor riesgo para
     * el cadete) — cada "recargo_dinero_transportado_umbral" pesos declarados suma
     * "recargo_dinero_transportado_monto" al precio, redondeando el tramo incompleto hacia
     * abajo. Umbral en 0 (o sin dinero declarado) desactiva el recargo.
     */
    public BigDecimal recargoPorDinero(BigDecimal montoDeclarado) {
        if (montoDeclarado == null || montoDeclarado.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal umbral = configuracionService.getBigDecimal("recargo_dinero_transportado_umbral", BigDecimal.ZERO);
        if (umbral.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal montoPorTramo = configuracionService.getBigDecimal("recargo_dinero_transportado_monto", BigDecimal.ZERO);
        long tramos = montoDeclarado.divideToIntegralValue(umbral).longValue();
        return montoPorTramo.multiply(BigDecimal.valueOf(tramos));
    }
}
