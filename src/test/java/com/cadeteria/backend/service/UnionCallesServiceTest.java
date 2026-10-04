package com.cadeteria.backend.service;

import com.cadeteria.backend.model.CalleUnion;
import com.cadeteria.backend.model.CuadraCoords;
import com.cadeteria.backend.model.DireccionAlias;
import com.cadeteria.backend.repository.CalleUnionRepository;
import com.cadeteria.backend.repository.CuadraCoordsRepository;
import com.cadeteria.backend.repository.DireccionAliasRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Unión de dos nombres de la misma calle (2026-10-03). Las filas viven en una lista que hace de tabla. */
class UnionCallesServiceTest {

    private static final String SMT = "San Miguel de Tucumán";
    private static final double LAT = -26.8182, LNG = -65.2143;
    /** ~20 m y ~500 m al norte. */
    private static final double LAT_20M = LAT + 0.00018, LAT_500M = LAT + 0.0045;

    private final List<CuadraCoords> tabla = new ArrayList<>();
    private CuadraCoordsRepository coords;
    private DireccionAliasRepository alias;
    private CalleUnionRepository uniones;
    private UnionCallesService service;

    @BeforeEach
    void setUp() {
        coords = mock(CuadraCoordsRepository.class);
        alias = mock(DireccionAliasRepository.class);
        uniones = mock(CalleUnionRepository.class);
        when(coords.findAll()).thenAnswer(i -> List.copyOf(tabla));
        when(coords.findByCalleCanonica(anyString())).thenAnswer(i ->
                tabla.stream().filter(c -> c.getCalleCanonica().equals(i.getArgument(0))).toList());
        when(coords.findByCalleCanonicaAndLocalidadAndCuadra(anyString(), anyString(), anyInt())).thenAnswer(i ->
                tabla.stream().filter(c -> c.getCalleCanonica().equals(i.getArgument(0)) && c.getLocalidad().equals(i.getArgument(1))
                        && c.getCuadra() == (int) i.getArgument(2)).findFirst());
        when(coords.findByLatBetweenAndLngBetween(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenAnswer(i ->
                tabla.stream().filter(c -> c.getLat() >= (double) i.getArgument(0) && c.getLat() <= (double) i.getArgument(1)
                        && c.getLng() >= (double) i.getArgument(2) && c.getLng() <= (double) i.getArgument(3)).toList());
        org.mockito.Mockito.doAnswer(i -> tabla.remove((CuadraCoords) i.getArgument(0))).when(coords).delete(any());
        when(alias.findByVarianteNorm(anyString())).thenReturn(Optional.empty());
        when(alias.findByCalleCanonica(anyString())).thenReturn(List.of());
        service = new UnionCallesService(coords, alias, uniones);
    }

    @Test
    void alAprenderConOtroNombreEnElMismoLugarQuedaElNombreCorto() {
        fila("suipacha", SMT, 700, LAT, LNG, "locationiq");
        fila("batalla de suipacha", SMT, 100, LAT_500M, LNG, "android_geocoder");

        String con = service.alAprender("batalla de suipacha", SMT, 700, LAT_20M, LNG);

        assertEquals("suipacha", con);
        // La otra cuadra que estaba con el nombre largo pasa al corto.
        assertTrue(tabla.stream().allMatch(c -> c.getCalleCanonica().equals("suipacha")));
        verify(uniones).save(any(CalleUnion.class));
        // Y el nombre largo sigue llevando a la calle.
        verify(alias).save(org.mockito.ArgumentMatchers.argThat((DireccionAlias a) ->
                a.getVarianteNorm().equals("batalla de suipacha") && a.getCalleCanonica().equals("suipacha")));
    }

    @Test
    void siElNombreNuevoEsElCortoLasFilasViejasPasanAEl() {
        fila("batalla de suipacha", SMT, 700, LAT, LNG, "android_geocoder");

        assertEquals("suipacha", service.alAprender("suipacha", SMT, 700, LAT_20M, LNG));
        assertEquals("suipacha", tabla.get(0).getCalleCanonica());
    }

    @Test
    void lejosOEnOtraCuadraOConOtroNombreNoSeUne() {
        fila("suipacha", SMT, 700, LAT, LNG, "locationiq");

        assertEquals("batalla de suipacha", service.alAprender("batalla de suipacha", SMT, 700, LAT_500M, LNG));
        assertEquals("batalla de suipacha", service.alAprender("batalla de suipacha", SMT, 800, LAT_20M, LNG));
        assertEquals("batalla de suipacha", service.alAprender("batalla de suipacha", "Yerba Buena", 700, LAT_20M, LNG));
        assertEquals("cordoba", service.alAprender("cordoba", SMT, 700, LAT_20M, LNG));
        verify(uniones, never()).save(any());
    }

    @Test
    void siEnOtraCuadraLosDosNombresEstanLejosSonCallesDistintas() {
        // "Perú" y "Camino del Perú" se tocan en la cuadra 1600, pero al 1700 están a 500 m.
        fila("peru", SMT, 1600, LAT, LNG, "nominatim");
        fila("peru", SMT, 1700, LAT, LNG + 0.001, "nominatim");
        fila("camino del peru", SMT, 1700, LAT_500M, LNG + 0.001, "android_geocoder");

        assertEquals("camino del peru", service.alAprender("camino del peru", SMT, 1600, LAT_20M, LNG));
        assertTrue(service.duplicadas(false).isEmpty());
        verify(uniones, never()).save(any());
    }

    @Test
    void laRevisionEncuentraLasDuplicadasYAlAplicarFusionaLaCuadraRepetida() {
        fila("suipacha", SMT, 700, LAT, LNG, "locationiq").setConfirmaciones(44);
        fila("batalla de suipacha", SMT, 700, LAT_20M, LNG, "cadete_gps").setConfirmaciones(4);
        fila("batalla de suipacha", SMT, 100, LAT_500M, LNG, "android_geocoder");
        fila("cordoba", SMT, 700, LAT, LNG, "nominatim");

        List<UnionCallesService.Union> simulada = service.duplicadas(false);

        assertEquals(1, simulada.size());
        assertEquals("batalla de suipacha", simulada.get(0).seFue());
        assertEquals("suipacha", simulada.get(0).queda());
        assertEquals(4, tabla.size(), "simular no toca nada");

        UnionCallesService.Union hecha = service.duplicadas(true).get(0);

        assertEquals(1, hecha.filasMovidas());
        assertEquals(1, hecha.filasFusionadas());
        CuadraCoords al700 = tabla.stream().filter(c -> c.getCalleCanonica().equals("suipacha") && c.getCuadra() == 700).findFirst().orElseThrow();
        // Quedó el punto del cadete (más confiable que el del buscador) y se sumaron las confirmaciones.
        assertEquals("cadete_gps", al700.getProveedor());
        assertEquals(LAT_20M, al700.getLat());
        assertEquals(48, al700.getConfirmaciones());
        assertFalse(tabla.stream().anyMatch(c -> c.getCalleCanonica().equals("batalla de suipacha")));
        assertEquals(3, tabla.size());
    }

    private CuadraCoords fila(String calle, String localidad, int cuadra, double lat, double lng, String proveedor) {
        CuadraCoords c = new CuadraCoords();
        c.setId(calle + "|" + localidad + "|" + cuadra);
        c.setCalleCanonica(calle);
        c.setLocalidad(localidad);
        c.setCuadra(cuadra);
        c.setLat(lat);
        c.setLng(lng);
        c.setApproximate(false);
        c.setProveedor(proveedor);
        c.setConfirmaciones(1);
        tabla.add(c);
        return c;
    }
}
