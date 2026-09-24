package com.cadeteria.backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Cotización solo por distancia (2026-09-24): $2000 hasta 2 km y $320 por km adicional. Antes
 * una zona con tarifa (la demo "Centro", $700, 8 km de radio) pisaba la distancia y todo salía $700.
 */
class CotizacionServiceTest {

    private ConfiguracionService config;
    private RutaService ruta;
    private CotizacionService service;

    @BeforeEach
    void setUp() {
        config = mock(ConfiguracionService.class);
        ruta = mock(RutaService.class);
        service = new CotizacionService(config, ruta);
        when(config.getBigDecimal(eq("precio_por_km"), eq(BigDecimal.ZERO))).thenReturn(new BigDecimal("320"));
        when(config.getBigDecimal(eq("precio_base_viaje"), eq(BigDecimal.ZERO))).thenReturn(new BigDecimal("2000"));
        when(config.getBigDecimal(eq("distancia_minima_km"), eq(BigDecimal.valueOf(2)))).thenReturn(BigDecimal.valueOf(2));
        when(config.getBigDecimal(eq("recargo_dinero_transportado_umbral"), eq(BigDecimal.ZERO))).thenReturn(BigDecimal.ZERO);
        when(config.getBigDecimal(eq("factor_linea_recta"), eq(new BigDecimal("1.4")))).thenReturn(new BigDecimal("1.4"));
    }

    private BigDecimal precioPara(double metros) {
        when(ruta.resumenSiDisponible(anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyString()))
                .thenReturn(Optional.of(new RutaService.Resumen(metros, 10)));
        return service.cotizar(-26.83, -65.20, -26.80, -65.25, null).orElseThrow().precioSugerido();
    }

    @Test
    void hastaDosKmSeCobraElMinimo() {
        assertEquals(new BigDecimal("2000"), precioPara(1500));
        assertEquals(new BigDecimal("2000"), precioPara(2000));
    }

    @Test
    void elExcedenteSeCobraPorKm() {
        // 5 km -> 2000 + 3 x 320 = 2960
        assertEquals(new BigDecimal("2960"), precioPara(5000));
        // 3,5 km -> 2000 + 1,5 x 320 = 2480
        assertEquals(new BigDecimal("2480"), precioPara(3500));
    }

    @Test
    void sinPrecioPorKmNoHaySugerencia() {
        when(config.getBigDecimal(eq("precio_por_km"), eq(BigDecimal.ZERO))).thenReturn(BigDecimal.ZERO);
        assertTrue(service.cotizar(-26.83, -65.20, -26.80, -65.25, null).isEmpty());
    }

    @Test
    void sinRutaUsaLineaRectaPorElFactor() {
        when(ruta.resumenSiDisponible(anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyString()))
                .thenReturn(Optional.empty());
        double recta = GeocodingService.distanciaKm(-26.83, -65.20, -26.80, -65.25);
        var c = service.cotizar(-26.83, -65.20, -26.80, -65.25, null).orElseThrow();
        assertEquals("DISTANCIA_ESTIMADA", c.metodo());
        assertEquals(recta * 1.4, c.distanciaKm(), 0.001);
    }
}
