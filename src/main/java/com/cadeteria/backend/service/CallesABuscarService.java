package com.cadeteria.backend.service;

import com.cadeteria.backend.common.BadRequestException;
import com.cadeteria.backend.model.CalleABuscarDescartada;
import com.cadeteria.backend.model.CuadraCoords;
import com.cadeteria.backend.repository.CalleABuscarDescartadaRepository;
import com.cadeteria.backend.repository.CuadraCoordsRepository;
import com.cadeteria.backend.repository.DireccionAliasRepository;
import com.cadeteria.backend.util.DireccionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Cuadras a buscar (2026-10-07): las que FALTAN en la base propia y que, si se cargan, destraban
 * más cálculo. Es la otra mitad de {@link CallesARevisarService}: aquella muestra lo que está mal,
 * esta lo que falta. El administrador busca la dirección en Google Maps y pega el link; la cuadra
 * entra con origen "link de Google" y {@link RellenoCallesService} completa en el momento las del
 * medio.
 * <p>
 * La lista no se guarda: se calcula cada vez con la base y el dibujo de las calles
 * ({@link TrazadoCalles}). Tres clases:
 * <ul>
 *   <li><b>INTERMEDIA</b>: entre dos cuadras reales hay más de {@value RellenoCallesService#HUECO_MAX}
 *       sin datos y el relleno no se anima con huecos tan largos; con la del medio quedan dos que sí.</li>
 *   <li><b>INICIO</b>: la calle se conoce recién desde una altura mayor que 0 y el dibujo sigue hacia el comienzo.</li>
 *   <li><b>FINAL</b>: el dibujo sigue más allá de la última cuadra real. La altura que se pide es
 *       estimada (largo del dibujo / largo de las cuadras de esa calle).</li>
 * </ul>
 * "Destraba" es cuántas cuadras nuevas calcularía el relleno si esa se cargara donde se la espera.
 */
@Service
public class CallesABuscarService {

    private static final Logger log = LoggerFactory.getLogger(CallesABuscarService.class);

    public static final String INTERMEDIA = "INTERMEDIA", INICIO = "INICIO", FINAL = "FINAL";

    /** Un punto del dibujo más lejos que esto de la línea de la calle es otra calle o una curva. */
    static final double AL_COSTADO_MAX_M = 80;
    /** Si el dibujo se corta más que esto, hasta ahí llega la calle. */
    static final double CORTE_MAX_M = 250;
    static final int MINIMO_REALES = 3, MINIMO_DESTRABA = 2;
    /** Hasta dónde puede caer el punto de Google respecto de donde se espera la cuadra. */
    static final double TOLERANCIA_M = 250, TOLERANCIA_FINAL_M = 400;
    static final double FUERA_DEL_DIBUJO_M = 80, OTRA_ALTURA_M = 60;
    private static final double PASO_DIBUJO_M = 50;

    public record ABuscar(String calleCanonica, String calle, String localidad, int cuadra, String tipo, int destraba,
                          double lat, double lng, String motivo, List<CallesARevisarService.Punto> cercanas) {}

    public record Resultado(boolean cargada, String mensaje, int completadas) {}

    private final CuadraCoordsRepository coordsRepository;
    private final DireccionAliasRepository aliasRepository;
    private final CalleABuscarDescartadaRepository descartadaRepository;
    private final TrazadoCalles trazado;
    private final LinkGoogleMapsService linkGoogleMaps;
    private final RellenoCallesService relleno;

    public CallesABuscarService(CuadraCoordsRepository coordsRepository, DireccionAliasRepository aliasRepository,
                                CalleABuscarDescartadaRepository descartadaRepository, TrazadoCalles trazado,
                                LinkGoogleMapsService linkGoogleMaps, RellenoCallesService relleno) {
        this.coordsRepository = coordsRepository;
        this.aliasRepository = aliasRepository;
        this.descartadaRepository = descartadaRepository;
        this.trazado = trazado;
        this.linkGoogleMaps = linkGoogleMaps;
        this.relleno = relleno;
    }

    /** Lo que conviene cargar, primero lo que más destraba. */
    @Transactional(readOnly = true)
    public List<ABuscar> lista() {
        Map<String, List<TrazadoCalles.Tramo>> dibujo = relleno.dibujoDeHoy();
        Set<String> descartadas = new HashSet<>();
        for (CalleABuscarDescartada d : descartadaRepository.findAll()) descartadas.add(d.getCalleCanonica() + "|" + d.getLocalidad() + "|" + d.getCuadra());
        Map<String, Map<String, TreeMap<Integer, CuadraCoords>>> porCalle = new TreeMap<>();
        for (CuadraCoords c : coordsRepository.findAll()) {
            if (!c.isApproximate()) {
                porCalle.computeIfAbsent(c.getCalleCanonica(), k -> new TreeMap<>()).computeIfAbsent(c.getLocalidad(), k -> new TreeMap<>()).put(c.getCuadra(), c);
            }
        }
        List<ABuscar> out = new ArrayList<>();
        for (Map.Entry<String, Map<String, TreeMap<Integer, CuadraCoords>>> e : porCalle.entrySet()) {
            List<TrazadoCalles.Tramo> tramos = dibujo.get(e.getKey());
            if (tramos == null || tramos.isEmpty()) continue;
            for (Map.Entry<String, TreeMap<Integer, CuadraCoords>> l : e.getValue().entrySet()) {
                for (ABuscar a : deUnaCalle(e.getKey(), l.getKey(), l.getValue(), tramos)) {
                    if (!descartadas.contains(a.calleCanonica() + "|" + a.localidad() + "|" + a.cuadra())) out.add(a);
                }
            }
        }
        out.sort(Comparator.comparingInt(ABuscar::destraba).reversed().thenComparing(ABuscar::calle).thenComparingInt(ABuscar::cuadra));
        return out;
    }

    /** Carga la cuadra con el link de Google Maps de esa dirección, si el punto cae donde se la espera. */
    @Transactional
    public Resultado cargarConLink(String calle, String localidad, int cuadra, String link, String quien) {
        ABuscar pedida = lista().stream().filter(a -> a.calleCanonica().equals(calle) && a.localidad().equals(localidad) && a.cuadra() == cuadra)
                .findFirst().orElseThrow(() -> new BadRequestException("Esa cuadra ya no está en la lista: volvé a cargarla."));
        LinkGoogleMapsService.ResultadoLink r = linkGoogleMaps.resolver(link);
        if (r.error() != null) return new Resultado(false, r.error(), 0);
        String otroNombre = CallesARevisarService.otroNombreSegunGoogle(calle, r.direccion());
        if (otroNombre != null) {
            return new Resultado(false, "Google le dice \"" + otroNombre + "\" a esa dirección, no \"" + pedida.calle()
                    + "\": puede ser otra calle o haber cambiado de nombre. No se guardó nada.", 0);
        }
        double[] p = TrazadoCalles.xy(r.lat(), r.lng());
        double alEsperado = TrazadoCalles.dist(p, TrazadoCalles.xy(pedida.lat(), pedida.lng()));
        if (alEsperado > (FINAL.equals(pedida.tipo()) ? TOLERANCIA_FINAL_M : TOLERANCIA_M)) {
            return new Resultado(false, "Google la ubica a " + Math.round(alEsperado) + " m de donde debería caer esa cuadra (el punto naranja). "
                    + "No se guardó nada: si Google numera distinto, mové el pin en Google Maps hasta la cuadra correcta y copiá ese link.", 0);
        }
        TrazadoCalles.Arrime alDibujo = TrazadoCalles.arrimar(relleno.dibujoDeHoy().get(calle), p);
        if (alDibujo != null && alDibujo.metros() > FUERA_DEL_DIBUJO_M) {
            return new Resultado(false, "Google la ubica a " + Math.round(alDibujo.metros()) + " m de por donde pasa esa calle. No se guardó nada.", 0);
        }
        for (CuadraCoords c : coordsRepository.findByCalleCanonica(calle)) {
            double aEsa = TrazadoCalles.dist(p, TrazadoCalles.xy(c.getLat(), c.getLng()));
            if (!c.isApproximate() && aEsa <= OTRA_ALTURA_M) {
                return new Resultado(false, "Google la ubica encima de la cuadra " + c.getCuadra() + " de esta misma calle (a " + Math.round(aEsa)
                        + " m): parece que numera distinto. No se guardó nada.", 0);
            }
        }
        CuadraCoords nueva = new CuadraCoords();
        nueva.setId(UUID.randomUUID().toString());
        nueva.setCalleCanonica(calle);
        nueva.setLocalidad(localidad);
        nueva.setCuadra(cuadra);
        nueva.setLat(r.lat());
        nueva.setLng(r.lng());
        nueva.setApproximate(false);
        nueva.setProveedor(DireccionCacheService.PROVEEDOR_GOOGLE_LINK);
        nueva.setConfirmaciones(1);
        nueva.setMuestras(1);
        coordsRepository.save(nueva);
        int completadas = relleno.rellenar(calle, localidad).nuevas();
        log.info("Cuadra a buscar cargada por {}: {} {} ({}), a {} m de donde se la esperaba; el cálculo completó {}.",
                quien, calle, cuadra, localidad, Math.round(alEsperado), completadas);
        return new Resultado(true, "Cargada " + pedida.calle() + " " + cuadra + ". El cálculo completó " + completadas
                + (completadas == 1 ? " cuadra más." : " cuadras más."), completadas);
    }

    /** Esa cuadra no existe (o no se quiere cargar): no se vuelve a pedir. */
    @Transactional
    public void descartar(String calle, String localidad, int cuadra, String quien) {
        CalleABuscarDescartada d = new CalleABuscarDescartada();
        d.setId(UUID.randomUUID().toString());
        d.setCalleCanonica(calle);
        d.setLocalidad(localidad);
        d.setCuadra(cuadra);
        d.setDescartadaPor(quien);
        descartadaRepository.save(d);
    }

    private List<ABuscar> deUnaCalle(String calle, String localidad, TreeMap<Integer, CuadraCoords> filas, List<TrazadoCalles.Tramo> tramos) {
        SortedMap<Integer, double[]> reales = new TreeMap<>();
        for (CuadraCoords c : filas.values()) {
            if (!RellenoCallesService.PROVEEDOR.equals(c.getProveedor())) reales.put(c.getCuadra(), TrazadoCalles.xy(c.getLat(), c.getLng()));
        }
        if (reales.size() < MINIMO_REALES) return List.of();
        List<Integer> orden = new ArrayList<>(reales.keySet());
        List<Double> largos = new ArrayList<>();
        for (int i = 0; i + 1 < orden.size(); i++) {
            int a = orden.get(i), b = orden.get(i + 1);
            double porCuadra = TrazadoCalles.dist(reales.get(a), reales.get(b)) / ((b - a) / 100d);
            if (b - a <= 300 && creible(porCuadra)) largos.add(porCuadra);
        }
        if (largos.size() < 2) return List.of();
        Collections.sort(largos);
        double porCuadra = largos.size() % 2 == 1 ? largos.get(largos.size() / 2) : (largos.get(largos.size() / 2 - 1) + largos.get(largos.size() / 2)) / 2;

        int yaCalculables = sinCargar(RellenoCallesService.huecos(reales, tramos).keySet(), filas);
        List<ABuscar> out = new ArrayList<>();

        // intermedias: huecos entre dos reales más largos que lo que el relleno acepta
        for (int i = 0; i + 1 < orden.size(); i++) {
            int a = orden.get(i), b = orden.get(i + 1), faltan = (b - a) / 100 - 1;
            double[] pa = reales.get(a), pb = reales.get(b);
            if (faltan <= RellenoCallesService.HUECO_MAX || !creible(TrazadoCalles.dist(pa, pb) / ((b - a) / 100d))) continue;
            int k = a + ((b - a) / 200) * 100;
            double f = (k - a) / (double) (b - a);
            TrazadoCalles.Arrime q = TrazadoCalles.arrimar(tramos, new double[]{pa[0] + f * (pb[0] - pa[0]), pa[1] + f * (pb[1] - pa[1])});
            if (q.metros() > RellenoCallesService.ARRIME_MAX_M) continue;
            agregar(out, calle, localidad, k, INTERMEDIA, q, reales, filas, tramos, yaCalculables,
                    "Entre la " + a + " y la " + b + " hay " + faltan + " cuadras sin datos reales: demasiadas para calcularlas solo.");
        }

        // por dónde va la calle: de la primera a la última real
        double[] primera = reales.get(orden.get(0)), ultima = reales.get(orden.get(orden.size() - 1));
        double largo = TrazadoCalles.dist(primera, ultima);
        if (largo < 100) return out;
        double[] eje = {(ultima[0] - primera[0]) / largo, (ultima[1] - primera[1]) / largo};
        List<double[]> dibujo = aLoLargo(tramos, primera, eje);   // {metros desde la primera sobre el eje, x, y}
        if (dibujo.isEmpty()) return out;

        // inicio: se conoce desde una altura mayor que 0
        if (orden.get(0) >= 200) {
            int k = Math.max(0, orden.get(0) - (RellenoCallesService.HUECO_MAX + 1) * 100);
            double sigue = -hastaDondeSigue(dibujo, 0, -1), haceFalta = (orden.get(0) - k) / 100d * porCuadra;
            if (sigue >= haceFalta - porCuadra) {
                agregar(out, calle, localidad, k, INICIO, enElDibujo(tramos, dibujo, -Math.min(haceFalta, sigue)), reales, filas, tramos, yaCalculables,
                        "La calle se conoce recién desde la " + orden.get(0) + " y el dibujo sigue hacia el comienzo.");
            }
        }

        // final: el dibujo sigue más allá de la última real
        double sigue = hastaDondeSigue(dibujo, largo, +1) - largo;
        int deMas = (int) (sigue / porCuadra);
        if (deMas >= MINIMO_DESTRABA + 1) {
            int salto = Math.min(deMas, RellenoCallesService.HUECO_MAX + 1), k = orden.get(orden.size() - 1) + salto * 100;
            agregar(out, calle, localidad, k, FINAL, enElDibujo(tramos, dibujo, largo + salto * porCuadra), reales, filas, tramos, yaCalculables,
                    "La última conocida es la " + orden.get(orden.size() - 1) + " y el dibujo de la calle sigue unos " + Math.round(sigue)
                            + " m más. La altura es estimada (" + Math.round(porCuadra) + " m por cuadra): si en Google no existe, probá con una más baja.");
        }
        return out;
    }

    /** Suma la cuadra a la lista si no está ya en la base, cae en esa localidad y destraba lo suficiente. */
    private void agregar(List<ABuscar> out, String calle, String localidad, int k, String tipo, TrazadoCalles.Arrime donde,
                         SortedMap<Integer, double[]> reales, TreeMap<Integer, CuadraCoords> filas, List<TrazadoCalles.Tramo> tramos,
                         int yaCalculables, String motivo) {
        if (donde == null || k < 0 || filas.containsKey(k)) return;
        if (donde.localidad() != null && !donde.localidad().isEmpty() && !donde.localidad().equals(localidad)) return;
        // Cuántas cuadras nuevas calcularía el relleno si esta se cargara donde se la espera.
        SortedMap<Integer, double[]> conEsta = new TreeMap<>(reales);
        conEsta.put(k, donde.punto());
        Set<Integer> calculables = new HashSet<>(RellenoCallesService.huecos(conEsta, tramos).keySet());
        calculables.remove(k);
        int destraba = sinCargar(calculables, filas) - yaCalculables;
        if (destraba < MINIMO_DESTRABA) return;
        List<CallesARevisarService.Punto> cercanas = new ArrayList<>();
        Integer antes = filas.lowerKey(k), despues = filas.higherKey(k);
        for (Integer vecina : new Integer[]{antes, despues}) {
            if (vecina != null) {
                CuadraCoords c = filas.get(vecina);
                cercanas.add(new CallesARevisarService.Punto(c.getCuadra(), c.getLocalidad(), c.getLat(), c.getLng()));
            }
        }
        double[] ll = TrazadoCalles.latlng(donde.punto());
        out.add(new ABuscar(calle, DireccionUtils.nombreParaMostrar(calle), localidad, k, tipo, destraba, ll[0], ll[1],
                motivo + " Con esta, el cálculo completa " + destraba + ".", cercanas));
    }

    private static int sinCargar(Set<Integer> cuadras, Map<Integer, CuadraCoords> filas) {
        int n = 0;
        for (int k : cuadras) if (!filas.containsKey(k)) n++;
        return n;
    }

    private static boolean creible(double porCuadra) {
        return porCuadra >= RellenoCallesService.M_POR_CUADRA_MIN && porCuadra <= RellenoCallesService.M_POR_CUADRA_MAX;
    }

    /** Puntos del dibujo que siguen la línea de la calle, cada {@value #PASO_DIBUJO_M} m, ordenados a lo largo del eje. */
    private static List<double[]> aLoLargo(List<TrazadoCalles.Tramo> tramos, double[] origen, double[] eje) {
        List<double[]> out = new ArrayList<>();
        for (TrazadoCalles.Tramo tramo : tramos) {
            double[][] pts = tramo.puntos();
            for (int i = 0; i < pts.length; i++) {
                int pasos = i + 1 < pts.length ? Math.max(1, (int) Math.ceil(TrazadoCalles.dist(pts[i], pts[i + 1]) / PASO_DIBUJO_M)) : 1;
                for (int s = 0; s < pasos; s++) {
                    double f = s / (double) pasos;
                    double x = i + 1 < pts.length ? pts[i][0] + f * (pts[i + 1][0] - pts[i][0]) : pts[i][0];
                    double y = i + 1 < pts.length ? pts[i][1] + f * (pts[i + 1][1] - pts[i][1]) : pts[i][1];
                    double dx = x - origen[0], dy = y - origen[1];
                    if (Math.abs(dx * -eje[1] + dy * eje[0]) <= AL_COSTADO_MAX_M) out.add(new double[]{dx * eje[0] + dy * eje[1], x, y});
                }
            }
        }
        out.sort(Comparator.comparingDouble(q -> q[0]));
        return out;
    }

    /** Hasta dónde sigue el dibujo sin cortarse desde {@code desde} (metros sobre el eje), yendo en {@code sentido}. */
    private static double hastaDondeSigue(List<double[]> dibujo, double desde, int sentido) {
        double fin = desde;
        for (int i = sentido > 0 ? 0 : dibujo.size() - 1; i >= 0 && i < dibujo.size(); i += sentido) {
            double t = dibujo.get(i)[0];
            if ((t - fin) * sentido <= 0) continue;
            if (Math.abs(t - fin) > CORTE_MAX_M) break;
            fin = t;
        }
        return fin;
    }

    /** El punto del dibujo a {@code t} metros sobre el eje, con la localidad de su tramo. */
    private static TrazadoCalles.Arrime enElDibujo(List<TrazadoCalles.Tramo> tramos, List<double[]> dibujo, double t) {
        double[] mejor = dibujo.get(0);
        for (double[] q : dibujo) if (Math.abs(q[0] - t) < Math.abs(mejor[0] - t)) mejor = q;
        return TrazadoCalles.arrimar(tramos, new double[]{mejor[1], mejor[2]});
    }
}
