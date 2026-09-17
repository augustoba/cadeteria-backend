package com.cadeteria.backend.service;

import com.cadeteria.backend.model.Zona;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

/**
 * Sugiere un precio de viaje sin que el admin tenga que tipearlo, a partir de origen y
 * destino — dos mecanismos, en este orden de prioridad. Sin destino no hay sugerencia
 * (antes se sugería solo con el origen, pero eso ignoraba a qué zona iba el viaje — un
 * pedido que sale del centro hacia una zona más lejana no puede cobrar el mínimo del
 * centro solo porque el origen cayó ahí; ver más abajo).
 * <p>
 * 1. **Por zona**: se resuelve la {@link Zona} tanto del origen como del destino
 *    (independientemente — {@link GeocodingService#resolverZona} ya elige la más chica si
 *    varias se superponen, ej. "4 avenidas" adentro de una zona más grande). Si alguna de
 *    las dos tiene {@code tarifaSugerida} cargada, se usa **la más cara de las dos que
 *    tengan tarifa** — así un viaje que sale de una zona barata (el centro) hacia una más
 *    cara (un barrio alejado), o al revés, siempre cobra como mínimo el precio de la zona
 *    más lejana, nunca el del centro. Si origen y destino caen en la misma zona, es
 *    simplemente el precio de esa zona.
 * 2. **Por distancia**: si ninguna de las dos zonas tiene tarifa cargada (o el punto no
 *    cae en ninguna zona), se calcula la distancia real por calle (via {@link RutaService}/
 *    OpenRouteService, perfil moto) — o en línea recta (Haversine) si no hay API key de ORS
 *    configurada o el servicio falla, para no romper la sugerencia por eso (misma idea que
 *    {@link RutaService#resumenSiDisponible}). Sobre esa distancia se cobra
 *    {@code precio_base_viaje} (el mínimo del viaje) hasta los primeros {@code distancia_minima_km}
 *    (default 2), y a partir de ahí {@code precio_por_km} por cada km adicional — no desde
 *    el km 0, ya que esos primeros km ya están cubiertos por el mínimo. Todo configurable
 *    desde el panel (Configuración → Tarifas). Si {@code precio_por_km} no está configurado
 *    (0), tampoco hay sugerencia — el admin sigue pudiendo cargar el precio a mano como
 *    siempre, esto es solo una ayuda, nunca bloquea el flujo.
 * <p>
 * Sobre cualquiera de los dos métodos se suma además un recargo por el dinero/valores que
 * el cliente declara transportar (riesgo del cadete): cada {@code recargo_dinero_transportado_umbral}
 * pesos declarados suma {@code recargo_dinero_transportado_monto} al precio sugerido,
 * redondeando el tramo incompleto hacia abajo (ej. con umbral 10000 y monto 100, declarar
 * $25000 suma $200, no $300). Ambos configurables desde el panel; umbral en 0 desactiva el recargo.
 * <p>
 * El orden de prioridad de arriba es el modo {@code metodo_cotizacion=AUTOMATICO} (default).
 * El admin puede forzarlo desde Configuración a {@code ZONA} (nunca calcula por distancia,
 * aunque haya precio por km cargado — si ninguna zona tiene tarifa, no hay sugerencia) o a
 * {@code DISTANCIA} (ignora la tarifa de zona por completo, siempre precio_base + km).
 */
@Service
public class CotizacionService {

    private final GeocodingService geocodingService;
    private final ConfiguracionService configuracionService;
    private final RutaService rutaService;

    public CotizacionService(GeocodingService geocodingService, ConfiguracionService configuracionService,
                              RutaService rutaService) {
        this.geocodingService = geocodingService;
        this.configuracionService = configuracionService;
        this.rutaService = rutaService;
    }

    public record Cotizacion(BigDecimal precioSugerido, String metodo, String zonaId, String zonaNombre, Double distanciaKm) {}

    public Optional<Cotizacion> cotizar(double origenLat, double origenLng, Double destinoLat, Double destinoLng,
                                         BigDecimal montoDeclarado) {
        if (destinoLat == null || destinoLng == null) {
            return Optional.empty();
        }
        String metodo = configuracionService.getString("metodo_cotizacion", "AUTOMATICO");

        if (!"DISTANCIA".equals(metodo)) {
            Optional<Zona> zonaMasCara = mejorZonaPorPrecio(
                    geocodingService.resolverZona(origenLat, origenLng),
                    geocodingService.resolverZona(destinoLat, destinoLng));
            if (zonaMasCara.isPresent()) {
                Zona z = zonaMasCara.get();
                BigDecimal precio = z.getTarifaSugerida().add(recargoPorDinero(montoDeclarado));
                return Optional.of(new Cotizacion(precio, "ZONA", z.getId(), z.getNombre(), null));
            }
            if ("ZONA".equals(metodo)) {
                return Optional.empty();
            }
        }

        BigDecimal precioPorKm = configuracionService.getBigDecimal("precio_por_km", BigDecimal.ZERO);
        if (precioPorKm.signum() <= 0) {
            return Optional.empty();
        }
        BigDecimal precioBase = configuracionService.getBigDecimal("precio_base_viaje", BigDecimal.ZERO);
        BigDecimal distanciaMinimaKm = configuracionService.getBigDecimal("distancia_minima_km", BigDecimal.valueOf(2));
        double km = rutaService.resumenSiDisponible(origenLat, origenLng, destinoLat, destinoLng, "MOTO")
                .map(r -> r.distanciaM() / 1000.0)
                .orElseGet(() -> GeocodingService.distanciaKm(origenLat, origenLng, destinoLat, destinoLng));
        double kmAdicionales = Math.max(0, km - distanciaMinimaKm.doubleValue());
        BigDecimal precio = precioBase.add(precioPorKm.multiply(BigDecimal.valueOf(kmAdicionales)))
                .add(recargoPorDinero(montoDeclarado))
                .setScale(0, RoundingMode.HALF_UP);
        return Optional.of(new Cotizacion(precio, "DISTANCIA", null, null, km));
    }

    /**
     * Recargo por el dinero/valores que el cliente declara transportar (mayor riesgo para
     * el cadete) — cada "recargo_dinero_transportado_umbral" pesos declarados suma
     * "recargo_dinero_transportado_monto" al precio, redondeando el tramo incompleto hacia
     * abajo. Umbral en 0 (o sin dinero declarado) desactiva el recargo.
     */
    private BigDecimal recargoPorDinero(BigDecimal montoDeclarado) {
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

    /** Entre la zona del origen y la del destino, la que tenga tarifa cargada y sea más cara (ver javadoc de la clase). */
    private Optional<Zona> mejorZonaPorPrecio(Optional<Zona> a, Optional<Zona> b) {
        Zona za = a.filter(z -> z.getTarifaSugerida() != null).orElse(null);
        Zona zb = b.filter(z -> z.getTarifaSugerida() != null).orElse(null);
        if (za == null) return Optional.ofNullable(zb);
        if (zb == null) return Optional.of(za);
        return Optional.of(za.getTarifaSugerida().compareTo(zb.getTarifaSugerida()) >= 0 ? za : zb);
    }
}
