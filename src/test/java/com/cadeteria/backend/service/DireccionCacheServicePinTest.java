package com.cadeteria.backend.service;

import com.cadeteria.backend.model.CuadraCoords;
import com.cadeteria.backend.model.DireccionAlias;
import com.cadeteria.backend.repository.CuadraCoordsRepository;
import com.cadeteria.backend.repository.DireccionAliasRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static com.cadeteria.backend.service.DireccionCacheService.PROVEEDOR_ANDROID_GEOCODER;
import static com.cadeteria.backend.service.DireccionCacheService.PROVEEDOR_CADETE_GPS;
import static com.cadeteria.backend.service.DireccionCacheService.PROVEEDOR_MANUAL;
import static com.cadeteria.backend.service.DireccionCacheService.PROVEEDOR_MANUAL_SIN_CONFIRMAR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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

/** Carril A del 2026-09-28: quién pisa a quién (3j), calles por el comienzo (3h) y el punto propio más cercano (3i). */
class DireccionCacheServicePinTest {

    private static final String SMT = "San Miguel de Tucumán";
    private static final double LAT = -26.8000, LNG = -65.2600;

    private DireccionAliasRepository aliasRepository;
    private CuadraCoordsRepository coordsRepository;
    private DireccionCacheService service;

    @BeforeEach
    void setUp() {
        aliasRepository = mock(DireccionAliasRepository.class);
        coordsRepository = mock(CuadraCoordsRepository.class);
        ConfiguracionService configuracion = mock(ConfiguracionService.class);
        when(configuracion.getInt(eq(DireccionCacheService.CONFIG_DIAS_GOOGLE), anyInt())).thenReturn(30);
        when(configuracion.getBoolean(anyString(), anyBoolean())).thenReturn(false);
        service = new DireccionCacheService(aliasRepository, coordsRepository, configuracion);
    }

    // ---- 3j: quién pisa a quién ----

    @Test
    void unPinDelOperadorNoPisaElPuntoQueConfirmoUnCadete() {
        CuadraCoords delCadete = fila(LAT, LNG, PROVEEDOR_CADETE_GPS);
        existe(delCadete);

        service.guardar("Colombia", 4695, "Colombia", SMT, LAT + 0.0004, LNG, false, PROVEEDOR_MANUAL);

        assertEquals(LAT, delCadete.getLat());
        assertEquals(PROVEEDOR_CADETE_GPS, delCadete.getProveedor());
    }

    @Test
    void elCadetePisaUnPinSinConfirmarYUnoConfirmado() {
        for (String pin : List.of(PROVEEDOR_MANUAL_SIN_CONFIRMAR, PROVEEDOR_MANUAL)) {
            CuadraCoords fila = fila(LAT, LNG, pin);
            existe(fila);

            service.guardar("Colombia", 4695, "Colombia", SMT, LAT + 0.0004, LNG, false, PROVEEDOR_CADETE_GPS);

            assertEquals(LAT + 0.0004, fila.getLat(), pin);
            assertEquals(PROVEEDOR_CADETE_GPS, fila.getProveedor(), pin);
        }
    }

    @Test
    void unBuscadorNoPisaNiSePromediaConUnPinSinConfirmar() {
        CuadraCoords pin = fila(LAT, LNG, PROVEEDOR_MANUAL_SIN_CONFIRMAR);
        existe(pin);

        service.guardar("Colombia", 4695, "Colombia", SMT, LAT + 0.001, LNG, false, "nominatim");

        assertEquals(LAT, pin.getLat());
        assertEquals(PROVEEDOR_MANUAL_SIN_CONFIRMAR, pin.getProveedor());
    }

    @Test
    void otroPinSinConfirmarReemplazaAlAnteriorPorqueEsElCorregido() {
        CuadraCoords pin = fila(LAT, LNG, PROVEEDOR_MANUAL_SIN_CONFIRMAR);
        existe(pin);

        service.guardar("Colombia", 4695, "Colombia", SMT, LAT + 0.0003, LNG, false, PROVEEDOR_MANUAL_SIN_CONFIRMAR);

        assertEquals(LAT + 0.0003, pin.getLat());
    }

    @Test
    void ordenDeConfianza() {
        assertTrue(DireccionCacheService.confianza(PROVEEDOR_CADETE_GPS) > DireccionCacheService.confianza(PROVEEDOR_MANUAL));
        assertTrue(DireccionCacheService.confianza(PROVEEDOR_MANUAL) > DireccionCacheService.confianza(PROVEEDOR_MANUAL_SIN_CONFIRMAR));
        assertTrue(DireccionCacheService.confianza(PROVEEDOR_MANUAL_SIN_CONFIRMAR) > DireccionCacheService.confianza("nominatim"));
    }

    // ---- 3h: calles por el comienzo ----

    @Test
    void conMenosDeCuatroLetrasNoBuscaPorElComienzo() {
        assertEquals(List.of(), service.callesQueEmpiezanCon("san"));
        verify(aliasRepository, never()).findTop50ByVarianteNormStartingWith(anyString());
    }

    @Test
    void variasFormasDeLaMismaCalleCuentanComoUna() {
        when(aliasRepository.findTop50ByVarianteNormStartingWith("lamad")).thenReturn(List.of(
                alias("lamadrid", "lamadrid"), alias("lamadrid 650", "lamadrid"), alias("lamadrid general", "lamadrid")));

        assertEquals(List.of("lamadrid"), service.callesQueEmpiezanCon("Lamad"));
    }

    @Test
    void devuelveLasDistintasCallesQueEmpiezanIgual() {
        when(aliasRepository.findTop50ByVarianteNormStartingWith("colom")).thenReturn(List.of(
                alias("colombia", "colombia"), alias("colombres", "colombres")));

        assertEquals(List.of("colombia", "colombres"), service.callesQueEmpiezanCon("colom"));
    }

    // ---- 3i: el punto propio más cercano ----

    @Test
    void prefiereLoMedidoEnLaCalleAUnPinMasCercanoEIgnoraLosBuscadores() {
        CuadraCoords nominatim = fila(LAT + 0.00005, LNG, "nominatim");          // ~5 m
        CuadraCoords pin = fila(LAT + 0.0001, LNG, PROVEEDOR_MANUAL);             // ~11 m
        CuadraCoords telefono = fila(LAT + 0.0002, LNG, PROVEEDOR_ANDROID_GEOCODER); // ~22 m
        when(coordsRepository.findByLatBetweenAndLngBetween(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(List.of(nominatim, pin, telefono));

        assertEquals(telefono, service.masCercanaPropia(LAT, LNG, 30));
    }

    @Test
    void fueraDelRadioNoHayPuntoPropio() {
        CuadraCoords lejos = fila(LAT + 0.0005, LNG, PROVEEDOR_CADETE_GPS); // ~55 m (en las esquinas del rectángulo)
        when(coordsRepository.findByLatBetweenAndLngBetween(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(List.of(lejos));

        assertNull(service.masCercanaPropia(LAT, LNG, 30));
    }

    private void existe(CuadraCoords c) {
        when(aliasRepository.findByVarianteNorm(anyString())).thenReturn(Optional.empty());
        when(coordsRepository.findByCalleCanonicaAndLocalidadAndCuadra("colombia", SMT, 4600)).thenReturn(Optional.of(c));
    }

    private static CuadraCoords fila(double lat, double lng, String proveedor) {
        CuadraCoords c = new CuadraCoords();
        c.setId("c-" + proveedor);
        c.setCalleCanonica("colombia");
        c.setLocalidad(SMT);
        c.setCuadra(4600);
        c.setLat(lat);
        c.setLng(lng);
        c.setProveedor(proveedor);
        c.setConfirmaciones(1);
        c.setMuestras(1);
        return c;
    }

    private static DireccionAlias alias(String variante, String canonica) {
        DireccionAlias a = new DireccionAlias();
        a.setVarianteNorm(variante);
        a.setCalleCanonica(canonica);
        a.setLocalidad(SMT);
        return a;
    }
}
