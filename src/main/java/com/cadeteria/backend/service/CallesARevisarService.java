package com.cadeteria.backend.service;

import com.cadeteria.backend.common.BadRequestException;
import com.cadeteria.backend.common.ResourceNotFoundException;
import com.cadeteria.backend.model.CalleDuda;
import com.cadeteria.backend.model.CuadraCoords;
import com.cadeteria.backend.repository.CalleDudaRepository;
import com.cadeteria.backend.repository.CuadraCoordsRepository;
import com.cadeteria.backend.util.CallesParecidas;
import com.cadeteria.backend.util.DireccionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Calles a revisar (2026-10-07): la lista de lo que la base propia de calles no puede dar por bueno
 * sola, para que lo resuelva el administrador desde el panel en vez de corregirlo con un .sql.
 * <p>
 * Entran dos clases de duda:
 * <ul>
 *   <li><b>de nombre</b>: dos nombres que se escriben casi igual ({@link CallesParecidas#forma}) y
 *       comparten localidad o tienen cuadras cerca ("Pasaje Bascary" y "Bascari");</li>
 *   <li><b>de ubicación</b>: una cuadra que no cae entre sus dos vecinas, la punta de una calle
 *       demasiado cerca o lejos de la cuadra de al lado, o dos cuadras en el mismo punto.</li>
 * </ul>
 * Se resuelven pegando el link de Google Maps de esa dirección: del link sale el punto y el sistema
 * decide solo cuando el resultado es claro (une los nombres, las deja como distintas o corrige la
 * cuadra). Si no es claro no decide: muestra las distancias y la persona elige. El punto de Google
 * queda como el bueno de la cuadra, salvo que ahí haya un GPS de un cadete en la puerta.
 */
@Service
public class CallesARevisarService {

    private static final Logger log = LoggerFactory.getLogger(CallesARevisarService.class);

    public static final String TIPO_NOMBRE = "NOMBRE", TIPO_UBICACION = "UBICACION";
    public static final String PENDIENTE = "PENDIENTE", UNIDA = "UNIDA", DISTINTAS = "DISTINTAS",
            CORREGIDA = "CORREGIDA", ESTA_BIEN = "ESTA_BIEN", NO_EXISTE = "NO_EXISTE", YA_CIERRA = "YA_CIERRA";

    /** El punto de Google a menos de esto de una cuadra: es esa cuadra. */
    static final int LINK_MISMA_M = 80;
    /** A más de esto de la otra calle: no es esa calle. */
    static final int LINK_LEJOS_M = 300;
    /** A más de esto de todas las cuadras de su calle: Google encontró otra cosa. */
    static final int LINK_FUERA_DE_LA_CALLE_M = 2000;

    public record Punto(int cuadra, String localidad, double lat, double lng) {}

    /** Una duda pendiente con lo que hace falta para mostrarla: su punto y los de las cuadras con las que se compara. */
    public record DudaVista(String id, String tipo, String calle, String localidad, int cuadra, String otraCalle,
                            String motivo, Double lat, Double lng, String proveedor, int usos,
                            Double linkLat, Double linkLng, List<Punto> cercanas) {}

    /** Cómo quedó la duda y la explicación para mostrar. */
    public record Resultado(String estado, String mensaje) {}

    private final CuadraCoordsRepository coordsRepository;
    private final CalleDudaRepository dudaRepository;
    private final UnionCallesService unionCalles;
    private final LinkGoogleMapsService linkGoogleMaps;

    public CallesARevisarService(CuadraCoordsRepository coordsRepository, CalleDudaRepository dudaRepository,
                                 UnionCallesService unionCalles, LinkGoogleMapsService linkGoogleMaps) {
        this.coordsRepository = coordsRepository;
        this.dudaRepository = dudaRepository;
        this.unionCalles = unionCalles;
        this.linkGoogleMaps = linkGoogleMaps;
    }

    /** Todas las noches a las 4:45, después de la limpieza de lo de Google (4:30). */
    @Scheduled(cron = "0 45 4 * * *", zone = "America/Argentina/Buenos_Aires")
    @Transactional
    public void actualizarProgramado() {
        int nuevas = actualizar();
        if (nuevas > 0) log.info("Calles a revisar: {} dudas nuevas.", nuevas);
    }

    /**
     * Repasa toda la base y anota las dudas que todavía no estaban (pendientes o ya resueltas: lo
     * resuelto no se vuelve a preguntar). Devuelve cuántas agregó.
     */
    @Transactional
    public int actualizar() {
        List<CuadraCoords> filas = coordsRepository.findAll().stream().filter(c -> !c.isApproximate()).toList();
        Set<String> conocidas = new HashSet<>();
        for (CalleDuda d : dudaRepository.findAll()) conocidas.add(clave(d.getTipo(), d.getCalleCanonica(), d.getLocalidad(), d.getCuadra(), d.getOtraCalle()));

        int nuevas = 0;
        for (CalleDuda d : dudasDeNombre(filas)) {
            if (conocidas.add(clave(d.getTipo(), d.getCalleCanonica(), d.getLocalidad(), d.getCuadra(), d.getOtraCalle()))) {
                dudaRepository.save(d);
                nuevas++;
            }
        }
        Map<CuadraCoords, String> sospechas = sospechas(filas);
        for (Map.Entry<CuadraCoords, String> s : sospechas.entrySet()) {
            CuadraCoords c = s.getKey();
            if (conocidas.add(clave(TIPO_UBICACION, c.getCalleCanonica(), c.getLocalidad(), c.getCuadra(), ""))) {
                dudaRepository.save(nueva(TIPO_UBICACION, c.getCalleCanonica(), c.getLocalidad(), c.getCuadra(), "", s.getValue()));
                nuevas++;
            }
        }
        cerrarLasQueYaCierran(sospechas);
        return nuevas;
    }

    /** Las pendientes, primero las de las cuadras más usadas. */
    @Transactional(readOnly = true)
    public List<DudaVista> pendientes() {
        List<DudaVista> out = new ArrayList<>();
        for (CalleDuda d : dudaRepository.findByEstado(PENDIENTE)) {
            CuadraCoords fila = coordsRepository.findByCalleCanonicaAndLocalidadAndCuadra(d.getCalleCanonica(), d.getLocalidad(), d.getCuadra()).orElse(null);
            List<Punto> cercanas = new ArrayList<>();
            if (TIPO_NOMBRE.equals(d.getTipo())) {
                for (CuadraCoords c : deAlturaParecida(d.getOtraCalle(), d.getCuadra())) cercanas.add(punto(c));
            } else {
                // La anterior y la siguiente de su calle.
                CuadraCoords ant = null, sig = null;
                for (CuadraCoords c : coordsRepository.findByCalleCanonica(d.getCalleCanonica())) {
                    if (c.isApproximate() || !c.getLocalidad().equals(d.getLocalidad())) continue;
                    if (c.getCuadra() < d.getCuadra() && (ant == null || c.getCuadra() > ant.getCuadra())) ant = c;
                    if (c.getCuadra() > d.getCuadra() && (sig == null || c.getCuadra() < sig.getCuadra())) sig = c;
                }
                if (ant != null) cercanas.add(punto(ant));
                if (sig != null) cercanas.add(punto(sig));
            }
            out.add(new DudaVista(d.getId(), d.getTipo(), DireccionUtils.nombreParaMostrar(d.getCalleCanonica()), d.getLocalidad(),
                    d.getCuadra(), d.getOtraCalle().isEmpty() ? "" : DireccionUtils.nombreParaMostrar(d.getOtraCalle()), d.getMotivo(),
                    fila == null ? null : fila.getLat(), fila == null ? null : fila.getLng(), fila == null ? null : fila.getProveedor(),
                    fila == null ? 0 : fila.getConfirmaciones(), d.getLinkLat(), d.getLinkLng(), cercanas));
        }
        out.sort(Comparator.comparingInt(DudaVista::usos).reversed().thenComparing(DudaVista::calle).thenComparingInt(DudaVista::cuadra));
        return out;
    }

    /** Resuelve una duda con el link de Google Maps de esa dirección. Si no alcanza para decidir, la duda sigue pendiente. */
    @Transactional
    public Resultado resolverConLink(String id, String link, String quien) {
        CalleDuda d = pendiente(id);
        LinkGoogleMapsService.ResultadoLink r = linkGoogleMaps.resolver(link);
        if (r.error() != null) return new Resultado(PENDIENTE, r.error());
        double lat = r.lat(), lng = r.lng();
        d.setLinkLat(lat);
        d.setLinkLng(lng);
        dudaRepository.save(d);
        return TIPO_NOMBRE.equals(d.getTipo()) ? nombreConLink(d, lat, lng, quien) : ubicacionConLink(d, lat, lng, quien);
    }

    /** Lo que elige la persona cuando no hay link o el link no alcanzó: MISMA, DISTINTAS, ESTA_BIEN o NO_EXISTE. */
    @Transactional
    public Resultado marcar(String id, String decision, String quien) {
        CalleDuda d = pendiente(id);
        boolean deNombre = TIPO_NOMBRE.equals(d.getTipo());
        switch (decision == null ? "" : decision) {
            case "MISMA" -> {
                if (!deNombre) throw new BadRequestException("Esa opción es solo para las dudas de nombre.");
                unionCalles.unirPorRevision(d.getCalleCanonica(), d.getOtraCalle(), d.getLocalidad(), d.getCuadra(), 0);
                return cerrar(d, UNIDA, "Unidas a mano: \"" + d.getCalleCanonica() + "\" pasa a ser \"" + d.getOtraCalle() + "\".", quien);
            }
            case "DISTINTAS" -> {
                if (!deNombre) throw new BadRequestException("Esa opción es solo para las dudas de nombre.");
                return cerrar(d, DISTINTAS, "Marcadas a mano como calles distintas.", quien);
            }
            case "ESTA_BIEN" -> {
                if (deNombre) throw new BadRequestException("Esa opción es solo para las dudas de ubicación.");
                return cerrar(d, ESTA_BIEN, "Marcada a mano: la cuadra está bien ubicada.", quien);
            }
            case "NO_EXISTE" -> {
                if (deNombre) throw new BadRequestException("Esa opción es solo para las dudas de ubicación.");
                fila(d).ifPresent(coordsRepository::delete);
                Resultado res = cerrar(d, NO_EXISTE, "Cuadra borrada: no existe.", quien);
                cerrarLasQueYaCierran(sospechas(coordsRepository.findAll().stream().filter(c -> !c.isApproximate()).toList()));
                return res;
            }
            default -> throw new BadRequestException("Decisión desconocida: " + decision);
        }
    }

    private Resultado nombreConLink(CalleDuda d, double lat, double lng, String quien) {
        String otra = d.getOtraCalle();
        double aLaOtra = Double.MAX_VALUE;
        for (CuadraCoords c : deAlturaParecida(otra, d.getCuadra())) aLaOtra = Math.min(aLaOtra, metros(lat, lng, c.getLat(), c.getLng()));
        double aLaPropia = fila(d).map(c -> metros(lat, lng, c.getLat(), c.getLng())).orElse(Double.MAX_VALUE);

        if (aLaOtra <= LINK_MISMA_M) {
            unionCalles.unirPorRevision(d.getCalleCanonica(), otra, d.getLocalidad(), d.getCuadra(), (int) Math.round(aLaOtra));
            corregirPunto(otra, d.getLocalidad(), d.getCuadra(), lat, lng);
            return cerrar(d, UNIDA, "Google la ubica a " + Math.round(aLaOtra) + " m de \"" + otra + "\": son la misma calle. Quedaron unidas.", quien);
        }
        if (aLaOtra > LINK_LEJOS_M && aLaPropia <= LINK_LEJOS_M) {
            corregirPunto(d.getCalleCanonica(), d.getLocalidad(), d.getCuadra(), lat, lng);
            return cerrar(d, DISTINTAS, "Google la ubica a " + Math.round(aLaPropia) + " m del punto que había y lejos de \"" + otra
                    + "\": son calles distintas.", quien);
        }
        return new Resultado(PENDIENTE, "No alcanza para decidir: Google la ubica a " + distancia(aLaOtra) + " de \"" + otra + "\" y a "
                + distancia(aLaPropia) + " del punto que había. Mirá el mapa y elegí.");
    }

    private Resultado ubicacionConLink(CalleDuda d, double lat, double lng, String quien) {
        CuadraCoords fila = fila(d).orElse(null);
        if (fila == null) return cerrar(d, YA_CIERRA, "Esa cuadra ya no está en la base.", quien);
        double alPunto = metros(lat, lng, fila.getLat(), fila.getLng());
        if (alPunto <= LINK_MISMA_M) {
            corregirPunto(fila.getCalleCanonica(), fila.getLocalidad(), fila.getCuadra(), lat, lng);
            return cerrar(d, ESTA_BIEN, "Google la ubica a " + Math.round(alPunto) + " m del punto que había: está bien.", quien);
        }
        if (DireccionCacheService.PROVEEDOR_CADETE_GPS.equals(fila.getProveedor())) {
            return new Resultado(PENDIENTE, "Google la ubica a " + distancia(alPunto) + ", pero ese punto lo marcó un cadete en la puerta: no se pisa. Mirá el mapa y elegí.");
        }
        double aLaCalle = Double.MAX_VALUE;
        for (CuadraCoords c : coordsRepository.findByCalleCanonica(fila.getCalleCanonica())) {
            if (c != fila && !c.isApproximate()) aLaCalle = Math.min(aLaCalle, metros(lat, lng, c.getLat(), c.getLng()));
        }
        if (aLaCalle != Double.MAX_VALUE && aLaCalle > LINK_FUERA_DE_LA_CALLE_M) {
            return new Resultado(PENDIENTE, "Google la ubica a " + distancia(aLaCalle) + " de la cuadra más cercana de esa calle: parece otra dirección. Revisá el link.");
        }
        corregirPunto(fila.getCalleCanonica(), fila.getLocalidad(), fila.getCuadra(), lat, lng);
        Resultado res = cerrar(d, CORREGIDA, "Cuadra corrida " + distancia(alPunto) + " al punto de Google.", quien);
        // Una cuadra mal puesta hace dudar de sus vecinas: con esta corregida, las que ya cierran salen de la lista.
        cerrarLasQueYaCierran(sospechas(coordsRepository.findAll().stream().filter(c -> !c.isApproximate()).toList()));
        return res;
    }

    /** Deja el punto de Google como el de esa cuadra, salvo que ahí haya un GPS de un cadete (más confiable). */
    private void corregirPunto(String calle, String localidad, int cuadra, double lat, double lng) {
        coordsRepository.findByCalleCanonicaAndLocalidadAndCuadra(calle, localidad, cuadra).ifPresent(c -> {
            if (DireccionCacheService.confianza(c.getProveedor()) > DireccionCacheService.confianza(DireccionCacheService.PROVEEDOR_GOOGLE_LINK)) return;
            c.setLat(lat);
            c.setLng(lng);
            c.setProveedor(DireccionCacheService.PROVEEDOR_GOOGLE_LINK);
            c.setMuestras(1);
            c.setApproximate(false);
            c.setCreadaEn(Instant.now());
            coordsRepository.save(c);
        });
    }

    private Resultado cerrar(CalleDuda d, String estado, String mensaje, String quien) {
        d.setEstado(estado);
        d.setResultado(mensaje);
        d.setResueltaEn(Instant.now());
        d.setResueltaPor(quien);
        dudaRepository.save(d);
        log.info("Calle a revisar resuelta por {}: {} {} ({}) -> {}. {}", quien, d.getCalleCanonica(), d.getCuadra(), d.getLocalidad(), estado, mensaje);
        return new Resultado(estado, mensaje);
    }

    /** Las de ubicación que seguían pendientes y ya no saltan en los controles salen solas de la lista. */
    private void cerrarLasQueYaCierran(Map<CuadraCoords, String> sospechas) {
        Set<String> saltan = new HashSet<>();
        for (CuadraCoords c : sospechas.keySet()) saltan.add(clave(TIPO_UBICACION, c.getCalleCanonica(), c.getLocalidad(), c.getCuadra(), ""));
        for (CalleDuda d : dudaRepository.findByEstado(PENDIENTE)) {
            if (!TIPO_UBICACION.equals(d.getTipo()) || saltan.contains(clave(d.getTipo(), d.getCalleCanonica(), d.getLocalidad(), d.getCuadra(), ""))) continue;
            d.setEstado(YA_CIERRA);
            d.setResultado("Dejó de saltar en los controles después de otra corrección.");
            d.setResueltaEn(Instant.now());
            dudaRepository.save(d);
        }
    }

    /** Pares de nombres con la misma forma que comparten localidad o tienen cuadras cerca. Queda el de más cuadras. */
    private List<CalleDuda> dudasDeNombre(List<CuadraCoords> filas) {
        Map<String, List<CuadraCoords>> porCalle = new LinkedHashMap<>();
        for (CuadraCoords c : filas) porCalle.computeIfAbsent(c.getCalleCanonica(), k -> new ArrayList<>()).add(c);
        Map<String, List<String>> porForma = new LinkedHashMap<>();
        for (String calle : porCalle.keySet()) {
            String forma = CallesParecidas.forma(calle);
            if (!forma.isEmpty()) porForma.computeIfAbsent(forma, k -> new ArrayList<>()).add(calle);
        }
        List<CalleDuda> out = new ArrayList<>();
        for (List<String> nombres : porForma.values()) {
            if (nombres.size() < 2) continue;
            nombres.sort(Comparator.comparingInt((String n) -> -porCalle.get(n).size()).thenComparingInt(String::length).thenComparing(n -> n));
            String queda = nombres.get(0);
            for (String seVa : nombres.subList(1, nombres.size())) {
                CuadraCoords masCerca = null;
                double distancia = Double.MAX_VALUE;
                int cerca = 0;
                for (CuadraCoords c : porCalle.get(seVa)) {
                    double d = Double.MAX_VALUE;
                    for (CuadraCoords o : porCalle.get(queda)) {
                        if (Math.abs(o.getCuadra() - c.getCuadra()) <= UnionCallesService.FORMA_ALTURA) d = Math.min(d, metros(c.getLat(), c.getLng(), o.getLat(), o.getLng()));
                    }
                    if (d <= UnionCallesService.FORMA_CERCA_M) cerca++;
                    if (d < distancia) {
                        distancia = d;
                        masCerca = c;
                    }
                }
                CuadraCoords enComun = porCalle.get(seVa).stream()
                        .filter(c -> porCalle.get(queda).stream().anyMatch(o -> o.getLocalidad().equals(c.getLocalidad()))).findFirst().orElse(null);
                // Mismo nombre en otra localidad y lejos: son dos calles, no hay nada que preguntar.
                if (cerca == 0 && enComun == null) continue;
                CuadraCoords muestra = cerca > 0 ? masCerca : enComun;
                int total = porCalle.get(seVa).size();
                String motivo = "Se escribe casi igual que \"" + DireccionUtils.nombreParaMostrar(queda) + "\". " + (cerca == 0
                        ? "Están en la misma localidad, pero no hay cuadras de altura parecida para comparar."
                        : (total == 1 ? "Su única cuadra está" : cerca + " de sus " + total + " cuadras están") + " cerca de esa calle (la "
                        + muestra.getCuadra() + ", a " + Math.round(distancia) + " m).");
                out.add(nueva(TIPO_NOMBRE, seVa, muestra.getLocalidad(), muestra.getCuadra(), queda, motivo));
            }
        }
        return out;
    }

    /**
     * Cuadras que no están donde sus vecinas dicen (mismos controles que el mapa de revisión del
     * 2026-10-06, importador-direcciones/osm/mapa.py, salvo "lejos del dibujo de la calle", que
     * necesita el trazado de OpenStreetMap y el backend no lo tiene).
     */
    static Map<CuadraCoords, String> sospechas(List<CuadraCoords> filas) {
        Map<String, List<CuadraCoords>> porCalle = new LinkedHashMap<>();
        for (CuadraCoords c : filas) porCalle.computeIfAbsent(c.getCalleCanonica() + "|" + c.getLocalidad(), k -> new ArrayList<>()).add(c);
        Map<CuadraCoords, String> out = new LinkedHashMap<>();
        for (List<CuadraCoords> lista : porCalle.values()) {
            lista.sort(Comparator.comparingInt(CuadraCoords::getCuadra));
            // Primero las que no caen entre sus dos vecinas: una así no sirve para juzgar a la de al lado.
            Set<CuadraCoords> fueraDeLinea = new HashSet<>();
            for (int i = 1; i + 1 < lista.size(); i++) {
                CuadraCoords ant = lista.get(i - 1), c = lista.get(i), sig = lista.get(i + 1);
                double porCuadra = metros(ant, sig) / ((sig.getCuadra() - ant.getCuadra()) / 100d);
                if (porCuadra < 50 || porCuadra > 220) continue;   // las vecinas no se creen entre sí
                double f = (c.getCuadra() - ant.getCuadra()) / (double) (sig.getCuadra() - ant.getCuadra());
                double error = metros(c.getLat(), c.getLng(), ant.getLat() + f * (sig.getLat() - ant.getLat()), ant.getLng() + f * (sig.getLng() - ant.getLng()));
                if (error > 150) {
                    fueraDeLinea.add(c);
                    anotar(out, c, "Está a " + Math.round(error) + " m de donde caería entre la " + ant.getCuadra() + " y la " + sig.getCuadra() + ".");
                }
            }
            List<CuadraCoords> confiables = lista.stream().filter(c -> !fueraDeLinea.contains(c)).toList();
            for (int i = 0; i < confiables.size(); i++) {
                CuadraCoords c = confiables.get(i);
                CuadraCoords ant = i > 0 ? confiables.get(i - 1) : null, sig = i + 1 < confiables.size() ? confiables.get(i + 1) : null;
                CuadraCoords vecina = ant != null ? ant : sig;
                if (vecina != null && (ant == null || sig == null)) {
                    double cuadras = Math.abs(c.getCuadra() - vecina.getCuadra()) / 100d;
                    double porCuadra = metros(c, vecina) / cuadras;
                    if (cuadras <= 6 && (porCuadra < 35 || porCuadra > 300)) {
                        anotar(out, c, "Es la punta de la calle y queda a " + Math.round(metros(c, vecina)) + " m de la " + vecina.getCuadra()
                                + " (" + Math.round(cuadras) + (cuadras == 1 ? " cuadra" : " cuadras") + ").");
                    }
                }
                if (sig != null && metros(c, sig) < 15) anotar(out, c, "Está en el mismo lugar que la " + sig.getCuadra() + ".");
            }
        }
        return out;
    }

    private static void anotar(Map<CuadraCoords, String> out, CuadraCoords c, String motivo) {
        out.merge(c, motivo, (a, b) -> a + " " + b);
    }

    /** Cuadras de una calle con altura parecida a {@code cuadra}, en cualquier localidad. */
    private List<CuadraCoords> deAlturaParecida(String calle, int cuadra) {
        return coordsRepository.findByCalleCanonica(calle).stream()
                .filter(c -> !c.isApproximate() && Math.abs(c.getCuadra() - cuadra) <= UnionCallesService.FORMA_ALTURA)
                .sorted(Comparator.comparingInt(CuadraCoords::getCuadra)).toList();
    }

    private Optional<CuadraCoords> fila(CalleDuda d) {
        return coordsRepository.findByCalleCanonicaAndLocalidadAndCuadra(d.getCalleCanonica(), d.getLocalidad(), d.getCuadra());
    }

    private CalleDuda pendiente(String id) {
        CalleDuda d = dudaRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Esa duda no existe."));
        if (!PENDIENTE.equals(d.getEstado())) throw new BadRequestException("Esa duda ya estaba resuelta.");
        return d;
    }

    private static CalleDuda nueva(String tipo, String calle, String localidad, int cuadra, String otraCalle, String motivo) {
        CalleDuda d = new CalleDuda();
        d.setId(UUID.randomUUID().toString());
        d.setTipo(tipo);
        d.setCalleCanonica(calle);
        d.setLocalidad(localidad);
        d.setCuadra(cuadra);
        d.setOtraCalle(otraCalle);
        d.setMotivo(motivo);
        d.setEstado(PENDIENTE);
        return d;
    }

    private static String clave(String tipo, String calle, String localidad, int cuadra, String otraCalle) {
        // En las de nombre la pregunta es por el par de nombres, no por la cuadra que se usó de muestra.
        return TIPO_NOMBRE.equals(tipo) ? tipo + "|" + calle + "|" + otraCalle : tipo + "|" + calle + "|" + localidad + "|" + cuadra;
    }

    private static Punto punto(CuadraCoords c) {
        return new Punto(c.getCuadra(), c.getLocalidad(), c.getLat(), c.getLng());
    }

    private static String distancia(double metros) {
        return metros == Double.MAX_VALUE ? "(sin cuadras para comparar)" : Math.round(metros) + " m";
    }

    private static double metros(CuadraCoords a, CuadraCoords b) {
        return metros(a.getLat(), a.getLng(), b.getLat(), b.getLng());
    }

    private static double metros(double lat1, double lng1, double lat2, double lng2) {
        return GeocodingService.distanciaKm(lat1, lng1, lat2, lng2) * 1000;
    }
}
