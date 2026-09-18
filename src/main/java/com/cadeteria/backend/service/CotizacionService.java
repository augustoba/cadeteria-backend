package com.cadeteria.backend.service;

import com.cadeteria.backend.model.Zona;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

/**
 * Sugiere un precio de viaje sin que el admin tenga que tipearlo, a partir de origen y
 * destino — dos mecanismos, en este orden de prioridad por default (el que llama puede
 * forzar uno de los dos con {@code metodoForzado}, ver {@link #cotizar}). Sin destino no
 * hay sugerencia (antes se sugería solo con el origen, pero eso ignoraba a qué zona iba
 * el viaje — un pedido que sale del centro hacia una zona más lejana no puede cobrar el
 * mínimo del centro solo porque el origen cayó ahí; ver más abajo).
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
 *    cae en ninguna zona), se calcula la distancia en línea recta origen→destino y se
 *    cobra {@code precio_base_viaje + precio_por_km * km}, ambos configurables desde el
 *    panel (Configuración → Tarifas). Si {@code precio_por_km} no está configurado (0),
 *    tampoco hay sugerencia — el admin sigue pudiendo cargar el precio a mano como siempre,
 *    esto es solo una ayuda, nunca bloquea el flujo.
 * <p>
 * **Recargo por dinero transportado** (mejora pedida por el dueño): si el pedido lleva
 * plata/valores ({@code montoDeclarado} &gt; 0), se le suma al precio sugerido
 * {@code recargo_dinero_monto} por cada {@code recargo_dinero_cada} pesos declarados
 * (redondeado hacia abajo — ej. con la config de ejemplo 10000/100, declarar $25000 suma
 * $200, no $250). Ambos configurables desde el panel, en 0 por default = desactivado,
 * mismo criterio que "precio_por_km" en 0.
 */
@Service
public class CotizacionService {

    private final GeocodingService geocodingService;
    private final ConfiguracionService configuracionService;

    public CotizacionService(GeocodingService geocodingService, ConfiguracionService configuracionService) {
        this.geocodingService = geocodingService;
        this.configuracionService = configuracionService;
    }

    public record Cotizacion(BigDecimal precioSugerido, String metodo, String zonaId, String zonaNombre,
                              Double distanciaKm, BigDecimal recargoPorDinero) {}

    /** metodoForzado: null = automático (zona si hay, si no distancia); "ZONA" o "DISTANCIA" para forzar uno solo. */
    public Optional<Cotizacion> cotizar(double origenLat, double origenLng, Double destinoLat, Double destinoLng,
                                         BigDecimal montoDeclarado, String metodoForzado) {
        if (destinoLat == null || destinoLng == null) {
            return Optional.empty();
        }

        BigDecimal recargo = calcularRecargoPorDinero(montoDeclarado);
        boolean probarZona = !"DISTANCIA".equals(metodoForzado);
        boolean probarDistancia = !"ZONA".equals(metodoForzado);

        if (probarZona) {
            Optional<Zona> zonaMasCara = mejorZonaPorPrecio(
                    geocodingService.resolverZona(origenLat, origenLng),
                    geocodingService.resolverZona(destinoLat, destinoLng));
            if (zonaMasCara.isPresent()) {
                Zona z = zonaMasCara.get();
                return Optional.of(new Cotizacion(z.getTarifaSugerida().add(recargo), "ZONA", z.getId(), z.getNombre(), null, recargo));
            }
        }

        if (probarDistancia) {
            BigDecimal precioPorKm = configuracionService.getBigDecimal("precio_por_km", BigDecimal.ZERO);
            if (precioPorKm.signum() > 0) {
                BigDecimal precioBase = configuracionService.getBigDecimal("precio_base_viaje", BigDecimal.ZERO);
                double km = GeocodingService.distanciaKm(origenLat, origenLng, destinoLat, destinoLng);
                BigDecimal precio = precioBase.add(precioPorKm.multiply(BigDecimal.valueOf(km)))
                        .add(recargo)
                        .setScale(0, RoundingMode.HALF_UP);
                return Optional.of(new Cotizacion(precio, "DISTANCIA", null, null, km, recargo));
            }
        }

        return Optional.empty();
    }

    private BigDecimal calcularRecargoPorDinero(BigDecimal montoDeclarado) {
        if (montoDeclarado == null || montoDeclarado.signum() <= 0) return BigDecimal.ZERO;
        BigDecimal cada = configuracionService.getBigDecimal("recargo_dinero_cada", BigDecimal.ZERO);
        BigDecimal monto = configuracionService.getBigDecimal("recargo_dinero_monto", BigDecimal.ZERO);
        if (cada.signum() <= 0 || monto.signum() <= 0) return BigDecimal.ZERO;
        BigDecimal escalones = montoDeclarado.divideToIntegralValue(cada);
        return escalones.multiply(monto);
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
