package com.cadeteria.backend.service;

import com.cadeteria.backend.config.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Calle que resolvió el Geocoder del teléfono mientras el cadete anda (2026-09-26). */
class GeocodingProxyServiceTelefonoTest {

    private DireccionCacheService cache;
    private GeocodingProxyService service;

    @BeforeEach
    void setUp() {
        cache = mock(DireccionCacheService.class);
        ConfiguracionService config = mock(ConfiguracionService.class);
        when(config.getInt(eq(GeocodingProxyService.CONFIG_GEOCODER_PRECISION_MAX), anyInt())).thenReturn(30);
        service = new GeocodingProxyService(new AppProperties(), mock(ApiKeyPoolService.class), cache, config);
    }

    @Test
    void conBuenaPrecisionSeGuardaConElNombreLargoYVenceComoGoogle() {
        assertTrue(service.aprenderDelTelefono("Av. Mate de Luna", 2450, "San Miguel de Tucumán", -26.82, -65.23, 12f));
        verify(cache).guardar(eq("Av. Mate de Luna"), eq(2450), eq("Avenida Mate de Luna"), eq("San Miguel de Tucumán"),
                eq(-26.82), eq(-65.23), eq(false), eq(DireccionCacheService.PROVEEDOR_ANDROID_GEOCODER));
    }

    @Test
    void conMalaPrecisionSinAlturaOSinPrecisionNoSeGuarda() {
        assertFalse(service.aprenderDelTelefono("San Martín", 650, "San Miguel de Tucumán", -26.82, -65.2, 80f));
        assertFalse(service.aprenderDelTelefono("San Martín", null, "San Miguel de Tucumán", -26.82, -65.2, 10f));
        assertFalse(service.aprenderDelTelefono("San Martín", 650, "San Miguel de Tucumán", -26.82, -65.2, null));
        verify(cache, never()).guardar(anyString(), anyInt(), anyString(), anyString(), anyDouble(), anyDouble(), anyBoolean(), anyString());
    }
}
