package com.cadeteria.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * El dibujo de las calles (2026-10-07): por dónde pasa cada una según OpenStreetMap (datos ODbL),
 * en {@code resources/calles/trazado.tsv.gz}. Lo arma {@code importador-direcciones/osm/exportar-trazado.py}
 * y viaja con el backend para que el relleno de huecos y la lista de cuadras a buscar corran acá
 * solos, sin exportar ni importar nada. Son ~5.500 tramos y ~42.000 puntos: se cargan a memoria la
 * primera vez que se usan.
 * <p>
 * Las cuentas se hacen en metros sobre un plano centrado en San Miguel de Tucumán, con las mismas
 * constantes que el importador ({@code relleno.py}), para que los dos calculen lo mismo.
 */
@Component
public class TrazadoCalles {

    private static final Logger log = LoggerFactory.getLogger(TrazadoCalles.class);
    private static final String ARCHIVO = "/calles/trazado.tsv.gz";

    private static final double LAT0 = -26.83, LNG0 = -65.22;
    private static final double KY = 111_320, KX = 111_320 * Math.cos(Math.toRadians(LAT0));

    /** Un tramo dibujado de una calle: la localidad donde cae su punto del medio y sus puntos, en metros. */
    public record Tramo(String localidad, double[][] puntos) {}

    /** El punto del dibujo más cercano a otro, a cuántos metros queda y en qué localidad está ese tramo. */
    public record Arrime(double[] punto, double metros, String localidad) {}

    private Map<String, List<Tramo>> tramos;

    public TrazadoCalles() {
    }

    /** Para las pruebas: un dibujo armado a mano. */
    public TrazadoCalles(Map<String, List<Tramo>> tramos) {
        this.tramos = tramos;
    }

    public static double[] xy(double lat, double lng) {
        return new double[]{(lng - LNG0) * KX, (lat - LAT0) * KY};
    }

    public static double[] latlng(double[] p) {
        return new double[]{LAT0 + p[1] / KY, LNG0 + p[0] / KX};
    }

    public static double dist(double[] a, double[] b) {
        return Math.hypot(a[0] - b[0], a[1] - b[1]);
    }

    /**
     * El dibujo con los nombres que las calles tienen hoy en la base: si un nombre de OpenStreetMap
     * se unió con otro, sus tramos van con el que quedó ({@code alias}: forma de escribirla -> calle).
     */
    public Map<String, List<Tramo>> conNombresDeHoy(Map<String, String> alias) {
        Map<String, List<Tramo>> out = new HashMap<>();
        for (Map.Entry<String, List<Tramo>> e : cargado().entrySet()) {
            out.computeIfAbsent(alias.getOrDefault(e.getKey(), e.getKey()), k -> new ArrayList<>()).addAll(e.getValue());
        }
        return out;
    }

    /** Null si la calle no tiene dibujo. */
    public static Arrime arrimar(List<Tramo> dibujo, double[] p) {
        Arrime mejor = null;
        if (dibujo == null) return null;
        for (Tramo tramo : dibujo) {
            double[][] pts = tramo.puntos();
            for (int i = 0; i + 1 < pts.length; i++) {
                double vx = pts[i + 1][0] - pts[i][0], vy = pts[i + 1][1] - pts[i][1];
                double largo2 = vx * vx + vy * vy;
                double t = largo2 == 0 ? 0 : Math.max(0, Math.min(1, ((p[0] - pts[i][0]) * vx + (p[1] - pts[i][1]) * vy) / largo2));
                double[] q = {pts[i][0] + t * vx, pts[i][1] + t * vy};
                double d = dist(p, q);
                if (mejor == null || d < mejor.metros()) mejor = new Arrime(q, d, tramo.localidad());
            }
        }
        return mejor;
    }

    private synchronized Map<String, List<Tramo>> cargado() {
        if (tramos != null) return tramos;
        Map<String, List<Tramo>> out = new HashMap<>();
        try (InputStream in = TrazadoCalles.class.getResourceAsStream(ARCHIVO)) {
            if (in == null) {
                log.warn("No está {}: sin el dibujo de las calles no se rellenan huecos ni se arma la lista de cuadras a buscar.", ARCHIVO);
            } else {
                BufferedReader r = new BufferedReader(new InputStreamReader(new GZIPInputStream(in), StandardCharsets.UTF_8));
                int n = 0;
                for (String linea; (linea = r.readLine()) != null; ) {
                    String[] p = linea.split("\t");
                    if (p.length < 3) continue;
                    String[] pares = p[2].split(";");
                    double[][] puntos = new double[pares.length][];
                    for (int i = 0; i < pares.length; i++) {
                        int coma = pares[i].indexOf(',');
                        puntos[i] = xy(Double.parseDouble(pares[i].substring(0, coma)), Double.parseDouble(pares[i].substring(coma + 1)));
                    }
                    out.computeIfAbsent(p[0], k -> new ArrayList<>()).add(new Tramo(p[1], puntos));
                    n++;
                }
                log.info("Dibujo de las calles cargado: {} tramos de {} calles.", n, out.size());
            }
        } catch (Exception e) {
            log.warn("No se pudo leer el dibujo de las calles ({}): {}", ARCHIVO, e.getMessage());
        }
        tramos = out;
        return tramos;
    }
}
