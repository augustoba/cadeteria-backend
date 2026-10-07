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
        when(coords.findById(anyString())).thenAnswer(i -> tabla.stream().filter(c -> c.getId().equals(i.getArgument(0))).findFirst());
        when(coords.save(any(CuadraCoords.class))).thenAnswer(i -> {
            CuadraCoords c = i.getArgument(0);
            if (!tabla.contains(c)) tabla.add(c);
            return c;
        });
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

    @Test
    void unaUnionSePuedeDeshacerYVuelveTodoComoEstaba() {
        fila("suipacha", SMT, 700, LAT, LNG, "locationiq").setConfirmaciones(44);
        fila("batalla de suipacha", SMT, 700, LAT_20M, LNG, "cadete_gps").setConfirmaciones(4);
        fila("batalla de suipacha", SMT, 100, LAT_500M, LNG, "android_geocoder");
        org.mockito.ArgumentCaptor<CalleUnion> hecha = org.mockito.ArgumentCaptor.forClass(CalleUnion.class);
        service.duplicadas(true);
        verify(uniones).save(hecha.capture());
        CalleUnion registro = hecha.getValue();
        assertTrue(registro.isSePuedeDeshacer());
        when(uniones.findById(registro.getId())).thenReturn(Optional.of(registro));

        service.deshacer(registro.getId());

        assertEquals(3, tabla.size());
        CuadraCoords suipacha = tabla.stream().filter(c -> c.getCalleCanonica().equals("suipacha")).findFirst().orElseThrow();
        assertEquals("locationiq", suipacha.getProveedor());
        assertEquals(LAT, suipacha.getLat());
        assertEquals(44, suipacha.getConfirmaciones());
        CuadraCoords batalla = tabla.stream().filter(c -> c.getCalleCanonica().equals("batalla de suipacha") && c.getCuadra() == 700).findFirst().orElseThrow();
        assertEquals("cadete_gps", batalla.getProveedor());
        assertEquals(LAT_20M, batalla.getLat());
        assertEquals(4, batalla.getConfirmaciones());
        assertTrue(tabla.stream().anyMatch(c -> c.getCalleCanonica().equals("batalla de suipacha") && c.getCuadra() == 100));
        assertFalse(registro.isSePuedeDeshacer(), "no se deshace dos veces");
    }

    // --- Regla de forma (2026-10-07): casos reales del 6 de octubre ---

    @Test
    void conLaMismaFormaSeGuardaConElNombreQueYaEstabaAunqueNoCaigaEnElMismoPunto() {
        // "Alberti Manuel M 400" del teléfono quedó a 62 m de "Manuel Alberti 400": por 2 m abrió otra calle.
        fila("manuel alberti", SMT, 400, LAT, LNG, "nominatim");

        assertEquals("manuel alberti", service.alAprender("alberti manuel m", SMT, 400, LAT + 0.00056, LNG));
        // No se mueve ni se fusiona nada: solo se guarda lo nuevo con el nombre que ya había.
        verify(uniones, never()).save(any());
        assertEquals(1, tabla.size());
    }

    @Test
    void laMismaFormaValeConUnaAlturaVecinaYEnLaLocalidadDeAlLado() {
        // Camino del Perú es el límite entre San Miguel y Yerba Buena: la 1100 solo estaba del otro lado.
        fila("camino del peru", "Yerba Buena", 1000, LAT, LNG, "osm");

        assertEquals("camino del peru", service.alAprender("avenida camino del peru", SMT, 1100, LAT + 0.0011, LNG));
    }

    @Test
    void siElNombreNuevoYaTieneCuadrasPropiasCercaEsOtraCalle() {
        // "Boulevard 9 de Julio" de Yerba Buena tiene sus 21 cuadras: no se le saca una para "9 de Julio".
        fila("9 de julio", "Yerba Buena", 100, LAT, LNG, "osm");
        fila("boulevard 9 de julio", "Yerba Buena", 0, LAT + 0.0018, LNG, "osm");

        assertEquals("boulevard 9 de julio", service.alAprender("boulevard 9 de julio", "Yerba Buena", 100, LAT + 0.0009, LNG));
    }

    @Test
    void laMismaFormaLejosOConOtraAlturaEsOtraCalle() {
        fila("sarmiento", "Yerba Buena", 100, LAT_500M, LNG, "osm");
        fila("sarmiento", "Yerba Buena", 900, LAT_20M, LNG, "osm");

        assertEquals("avenida sarmiento", service.alAprender("avenida sarmiento", SMT, 100, LAT, LNG));
    }

    @Test
    void unNumeroDistintoNoEsLaMismaForma() {
        fila("diagonal 1", SMT, 1000, LAT, LNG, "osm");

        assertEquals("diagonal 2", service.alAprender("diagonal 2", SMT, 1000, LAT + 0.0009, LNG));
    }

    @Test
    void conDosCallesDeLaMismaFormaCercaNoSeAdivina() {
        fila("mitre", SMT, 400, LAT, LNG, "osm");
        fila("pasaje mitre", SMT, 400, LAT + 0.0009, LNG, "osm");

        assertEquals("avenida mitre", service.alAprender("avenida mitre", SMT, 400, LAT + 0.0005, LNG));
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
