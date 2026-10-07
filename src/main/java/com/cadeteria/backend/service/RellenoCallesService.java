package com.cadeteria.backend.service;

import com.cadeteria.backend.model.CuadraCoords;
import com.cadeteria.backend.model.DireccionAlias;
import com.cadeteria.backend.repository.CuadraCoordsRepository;
import com.cadeteria.backend.repository.DireccionAliasRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Relleno de huecos (2026-10-07): entre dos cuadras conocidas de la misma calle, las del medio se
 * reparten parejo y se arriman al dibujo de la calle. Es el paso 1 de {@code relleno.py} del
 * importador traído al backend, con sus mismos límites, para que corra solo cada vez que se carga o
 * se corrige una cuadra en la pantalla Calles (antes había que exportar la base, calcular afuera y
 * cargar un .sql).
 * <p>
 * Las calculadas se guardan como {@value #PROVEEDOR}. Solo se apoyan en cuadras reales (nunca en
 * otra calculada) y nunca pisan una real: si una real se corrige, las calculadas a su alrededor se
 * vuelven a ubicar.
 */
@Service
public class RellenoCallesService {

    private static final Logger log = LoggerFactory.getLogger(RellenoCallesService.class);

    public static final String PROVEEDOR = "osm_relleno";
    /** Cuadras seguidas sin datos que se aceptan rellenar (medido en el importador: mismo error con 1 que con 8). */
    public static final int HUECO_MAX = 8;
    /** Largo creíble de una cuadra de 100 números; fuera de esto la numeración es rara y no se rellena. */
    public static final double M_POR_CUADRA_MIN = 60, M_POR_CUADRA_MAX = 200;
    /** Si el punto calculado queda más lejos que esto del dibujo de la calle, se descarta. */
    public static final double ARRIME_MAX_M = 60;
    private static final double SE_MOVIO_M = 5;

    public record Resultado(int nuevas, int movidas) {}

    private final CuadraCoordsRepository coordsRepository;
    private final DireccionAliasRepository aliasRepository;
    private final TrazadoCalles trazado;

    public RellenoCallesService(CuadraCoordsRepository coordsRepository, DireccionAliasRepository aliasRepository, TrazadoCalles trazado) {
        this.coordsRepository = coordsRepository;
        this.aliasRepository = aliasRepository;
        this.trazado = trazado;
    }

    /** El dibujo con los nombres de hoy (ver {@link TrazadoCalles#conNombresDeHoy}). */
    public Map<String, List<TrazadoCalles.Tramo>> dibujoDeHoy() {
        Map<String, String> alias = new HashMap<>();
        for (DireccionAlias a : aliasRepository.findAll()) alias.put(a.getVarianteNorm(), a.getCalleCanonica());
        return trazado.conNombresDeHoy(alias);
    }

    /** Completa (o vuelve a ubicar) las cuadras calculadas de una calle en una localidad. */
    @Transactional
    public Resultado rellenar(String calle, String localidad) {
        List<CuadraCoords> filas = coordsRepository.findByCalleCanonica(calle).stream()
                .filter(c -> !c.isApproximate() && c.getLocalidad().equals(localidad)).toList();
        SortedMap<Integer, double[]> reales = new TreeMap<>();
        Map<Integer, CuadraCoords> porCuadra = new HashMap<>();
        for (CuadraCoords c : filas) {
            porCuadra.put(c.getCuadra(), c);
            if (!PROVEEDOR.equals(c.getProveedor())) reales.put(c.getCuadra(), TrazadoCalles.xy(c.getLat(), c.getLng()));
        }
        int nuevas = 0, movidas = 0;
        for (Map.Entry<Integer, double[]> e : huecos(reales, dibujoDeHoy().get(calle)).entrySet()) {
            double[] ll = TrazadoCalles.latlng(e.getValue());
            CuadraCoords ya = porCuadra.get(e.getKey());
            if (ya == null) {
                CuadraCoords c = new CuadraCoords();
                c.setId(UUID.randomUUID().toString());
                c.setCalleCanonica(calle);
                c.setLocalidad(localidad);
                c.setCuadra(e.getKey());
                c.setLat(ll[0]);
                c.setLng(ll[1]);
                c.setApproximate(false);
                c.setProveedor(PROVEEDOR);
                c.setConfirmaciones(1);
                c.setMuestras(1);
                coordsRepository.save(c);
                nuevas++;
            } else if (PROVEEDOR.equals(ya.getProveedor())
                    && TrazadoCalles.dist(TrazadoCalles.xy(ya.getLat(), ya.getLng()), e.getValue()) > SE_MOVIO_M) {
                ya.setLat(ll[0]);
                ya.setLng(ll[1]);
                coordsRepository.save(ya);
                movidas++;
            }
        }
        if (nuevas + movidas > 0) log.info("Relleno de {} ({}): {} cuadras nuevas, {} vueltas a ubicar.", calle, localidad, nuevas, movidas);
        return new Resultado(nuevas, movidas);
    }

    /**
     * Las cuadras que se pueden calcular entre las conocidas de una calle (cuadra -> punto en metros).
     * Mismo criterio que {@code rellenar_hueco} de relleno.py: hasta {@value #HUECO_MAX} seguidas, con
     * un largo de cuadra creíble y arrimadas al dibujo; sin dibujo, solo huecos de una cuadra.
     */
    static Map<Integer, double[]> huecos(SortedMap<Integer, double[]> conocidas, List<TrazadoCalles.Tramo> dibujo) {
        Map<Integer, double[]> out = new LinkedHashMap<>();
        Integer a = null;
        for (int b : conocidas.keySet()) {
            if (a != null) {
                double[] pa = conocidas.get(a), pb = conocidas.get(b);
                int cuadras = (b - a) / 100;
                double porCuadra = cuadras == 0 ? 0 : TrazadoCalles.dist(pa, pb) / cuadras;
                if (cuadras - 1 <= HUECO_MAX && porCuadra >= M_POR_CUADRA_MIN && porCuadra <= M_POR_CUADRA_MAX) {
                    for (int k = a + 100; k < b; k += 100) {
                        double f = (k - a) / (double) (b - a);
                        double[] p = {pa[0] + f * (pb[0] - pa[0]), pa[1] + f * (pb[1] - pa[1])};
                        TrazadoCalles.Arrime q = dibujo == null || dibujo.isEmpty() ? null : TrazadoCalles.arrimar(dibujo, p);
                        if (q == null) {
                            if (cuadras <= 2) out.put(k, p);
                        } else if (q.metros() <= ARRIME_MAX_M) {
                            out.put(k, q.punto());
                        }
                    }
                }
            }
            a = b;
        }
        return out;
    }
}
