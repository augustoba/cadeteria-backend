package com.cadeteria.backend.service;

import com.cadeteria.backend.model.CuadraCoords;
import com.cadeteria.backend.repository.CuadraCoordsRepository;
import com.cadeteria.backend.repository.DireccionAliasRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Relleno de huecos en el backend (2026-10-07): tiene que dar lo mismo que relleno.py del importador. */
class RellenoCallesServiceTest {

    private static final String SMT = "San Miguel de Tucumán";
    private static final double LAT = -26.8182, LNG = -65.2143, M100 = 0.0009;

    private final List<CuadraCoords> tabla = new ArrayList<>();
    private RellenoCallesService service;

    @BeforeEach
    void setUp() {
        CuadraCoordsRepository coords = mock(CuadraCoordsRepository.class);
        DireccionAliasRepository alias = mock(DireccionAliasRepository.class);
        when(alias.findAll()).thenReturn(List.of());
        when(coords.findByCalleCanonica(anyString())).thenAnswer(i ->
                tabla.stream().filter(c -> c.getCalleCanonica().equals(i.getArgument(0))).toList());
        when(coords.save(any(CuadraCoords.class))).thenAnswer(i -> {
            CuadraCoords c = i.getArgument(0);
            if (!tabla.contains(c)) tabla.add(c);
            return c;
        });
        // "lavalle": una calle recta de 2 km hacia el norte, dibujada en un solo tramo.
        TrazadoCalles trazado = new TrazadoCalles(Map.of("lavalle", List.of(new TrazadoCalles.Tramo(SMT, new double[][]{
                TrazadoCalles.xy(LAT, LNG), TrazadoCalles.xy(LAT + 20 * M100, LNG)}))));
        service = new RellenoCallesService(coords, alias, trazado);
    }

    @Test
    void sobreCallesRealesDaLoMismoQueElImportador() throws Exception {
        Map<String, TreeMap<Integer, double[]>> conocidas = new HashMap<>();
        Map<String, double[]> esperadas = new HashMap<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                getClass().getResourceAsStream("/calles/relleno-esperado.tsv"), StandardCharsets.UTF_8))) {
            for (String l; (l = r.readLine()) != null; ) {
                if (l.startsWith("#") || l.isBlank()) continue;
                String[] p = l.split("\t");
                double[] punto = TrazadoCalles.xy(Double.parseDouble(p[4]), Double.parseDouble(p[5]));
                if (p[0].equals("CONOCIDA")) conocidas.computeIfAbsent(p[1] + "|" + p[2], k -> new TreeMap<>()).put(Integer.parseInt(p[3]), punto);
                else esperadas.put(p[1] + "|" + p[2] + "|" + p[3], punto);
            }
        }
        Map<String, List<TrazadoCalles.Tramo>> dibujo = new TrazadoCalles().conNombresDeHoy(Map.of());

        Map<String, double[]> calculadas = new HashMap<>();
        for (Map.Entry<String, TreeMap<Integer, double[]>> e : conocidas.entrySet()) {
            String calle = e.getKey().substring(0, e.getKey().indexOf('|'));
            RellenoCallesService.huecos(e.getValue(), dibujo.getOrDefault(calle, List.of()))
                    .forEach((k, p) -> calculadas.put(e.getKey() + "|" + k, p));
        }

        assertEquals(271, esperadas.size(), "el archivo de comparación");
        assertEquals(esperadas.keySet(), calculadas.keySet());
        for (Map.Entry<String, double[]> e : esperadas.entrySet()) {
            double[] c = calculadas.get(e.getKey());
            // El dibujo viaja con 6 decimales (10 cm): por eso no es idéntico al centímetro.
            assertTrue(Math.hypot(c[0] - e.getValue()[0], c[1] - e.getValue()[1]) < 2, e.getKey());
        }
    }

    @Test
    void completaLasCuadrasEntreDosConocidasYLasDejaComoCalculadas() {
        fila("lavalle", 100, LAT + M100, "osm");
        fila("lavalle", 500, LAT + 5 * M100, "google_link");

        RellenoCallesService.Resultado r = service.rellenar("lavalle", SMT);

        assertEquals(3, r.nuevas());
        CuadraCoords la300 = tabla.stream().filter(c -> c.getCuadra() == 300).findFirst().orElse(null);
        assertNotNull(la300);
        assertEquals("osm_relleno", la300.getProveedor());
        assertEquals(LAT + 3 * M100, la300.getLat(), 0.00002);
    }

    @Test
    void alCorregirseUnaConocidaLasCalculadasSeVuelvenAUbicar() {
        fila("lavalle", 100, LAT + M100, "osm");
        CuadraCoords calculada = fila("lavalle", 200, LAT + 4 * M100, "osm_relleno");   // quedó de cuando la 300 estaba mal
        fila("lavalle", 300, LAT + 3 * M100, "google_link");

        RellenoCallesService.Resultado r = service.rellenar("lavalle", SMT);

        assertEquals(0, r.nuevas());
        assertEquals(1, r.movidas());
        assertEquals(LAT + 2 * M100, calculada.getLat(), 0.00002);
    }

    @Test
    void noRellenaUnHuecoDeMasDeOchoCuadrasNiPisaUnaCuadraReal() {
        fila("lavalle", 0, LAT, "osm");
        CuadraCoords real = fila("lavalle", 100, LAT + 1.3 * M100, "nominatim");
        fila("lavalle", 200, LAT + 2 * M100, "osm");
        fila("lavalle", 1200, LAT + 12 * M100, "osm");

        RellenoCallesService.Resultado r = service.rellenar("lavalle", SMT);

        assertEquals(0, r.nuevas() + r.movidas());
        assertEquals(LAT + 1.3 * M100, real.getLat());
    }

    private CuadraCoords fila(String calle, int cuadra, double lat, String proveedor) {
        CuadraCoords c = new CuadraCoords();
        c.setId(calle + "|" + cuadra);
        c.setCalleCanonica(calle);
        c.setLocalidad(SMT);
        c.setCuadra(cuadra);
        c.setLat(lat);
        c.setLng(LNG);
        c.setApproximate(false);
        c.setProveedor(proveedor);
        c.setConfirmaciones(1);
        tabla.add(c);
        return c;
    }
}
