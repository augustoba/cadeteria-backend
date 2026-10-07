package com.cadeteria.backend.service;

import com.cadeteria.backend.common.BadRequestException;
import com.cadeteria.backend.common.ResourceNotFoundException;
import com.cadeteria.backend.model.CalleUnion;
import com.cadeteria.backend.model.CuadraCoords;
import com.cadeteria.backend.model.DireccionAlias;
import com.cadeteria.backend.repository.CalleUnionRepository;
import com.cadeteria.backend.repository.CuadraCoordsRepository;
import com.cadeteria.backend.repository.DireccionAliasRepository;
import com.cadeteria.backend.util.CallesParecidas;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Une en un solo nombre las calles que la base aprendió con dos (2026-10-03). El teléfono del cadete
 * llama a veces "Batalla de Suipacha" a la calle que el mapa y la gente llaman "Suipacha": cada
 * nombre abría su fila, el buscador mostraba dos opciones para la misma dirección y lo que
 * confirmaba un cadete con un nombre no corregía la fila del otro.
 * <p>
 * Dos nombres son la misma calle cuando se cumplen las tres cosas:
 * <ol>
 *   <li>tienen una cuadra con el mismo número, en la misma localidad;</li>
 *   <li>esas dos cuadras están a menos de {@value #RADIO_MISMA_CALLE_M} m;</li>
 *   <li>los nombres comparten las palabras que los distinguen: uno contiene al otro, sin contar
 *       avenida/pasaje/general/de (ver {@link CallesParecidas#palabrasClave}).</li>
 * </ol>
 * Y no hay ninguna otra cuadra donde los dos nombres estén lejos (más de
 * {@value #CONTRADICCION_M} m): ahí serían dos calles distintas que se cruzan o se tocan.
 * Queda el nombre con menos palabras ("Suipacha"). Cada unión se registra en {@link CalleUnion}.
 */
@Service
public class UnionCallesService {

    private static final Logger log = LoggerFactory.getLogger(UnionCallesService.class);

    static final int RADIO_MISMA_CALLE_M = 60;
    static final int CONTRADICCION_M = 300;
    static final int FORMA_CERCA_M = 300;
    static final int FORMA_ALTURA = 200;
    public static final String ORIGEN_AL_APRENDER = "al aprender";
    public static final String ORIGEN_REVISION = "revisión de duplicadas";
    public static final String ORIGEN_A_REVISAR = "calles a revisar";
    /** Al unir desde "calles a revisar" solo pasan las cuadras a menos de esto de la calle que queda. */
    static final int ZONA_M = 1500;

    private static final com.fasterxml.jackson.databind.ObjectMapper JSON = new com.fasterxml.jackson.databind.ObjectMapper();

    /** Una cuadra tal como estaba antes de una unión. */
    record FilaAntes(String id, String calle, String localidad, int cuadra, double lat, double lng, boolean approximate,
                     String proveedor, int confirmaciones, int muestras, long creadaEn) {}

    /** A qué calle llevaba una forma de escribirla antes de una unión. */
    record AliasAntes(String id, String calle) {}

    /** Lo que hace falta para deshacer una unión: las cuadras y los alias que tocó y el alias que creó. */
    record Respaldo(List<FilaAntes> filas, List<AliasAntes> alias, String aliasCreado) {}

    /** Lo que se unió o se uniría: {@code seFue} deja de existir como nombre propio. */
    public record Union(String seFue, String queda, String localidad, int cuadra, int distanciaM,
                        int filasMovidas, int filasFusionadas) {}

    private final CuadraCoordsRepository coordsRepository;
    private final DireccionAliasRepository aliasRepository;
    private final CalleUnionRepository unionRepository;

    public UnionCallesService(CuadraCoordsRepository coordsRepository, DireccionAliasRepository aliasRepository,
                              CalleUnionRepository unionRepository) {
        this.coordsRepository = coordsRepository;
        this.aliasRepository = aliasRepository;
        this.unionRepository = unionRepository;
    }

    /**
     * Al aprender una cuadra con un nombre que todavía no la tiene: si en ese mismo lugar ya está
     * esa cuadra con otro nombre de la misma calle, las une y devuelve el nombre que queda (con el
     * que hay que guardar). Si no hay nada que unir, o hay más de una candidata, devuelve el mismo.
     */
    @Transactional
    public String alAprender(String canonica, String localidad, int cuadra, double lat, double lng) {
        if (coordsRepository.findByCalleCanonicaAndLocalidadAndCuadra(canonica, localidad, cuadra).isPresent()) return canonica;
        String porForma = mismaFormaCerca(canonica, cuadra, lat, lng);
        if (porForma != null) {
            log.info("Calle \"{}\" {} guardada como \"{}\": mismo nombre escrito de otra forma y cuadras de esa calle cerca.",
                    canonica, cuadra, porForma);
            return porForma;
        }
        Set<String> palabras = palabras(canonica);
        if (noDistingue(palabras)) return canonica;

        double dLat = RADIO_MISMA_CALLE_M / 111_320d;
        double dLng = RADIO_MISMA_CALLE_M / (111_320d * Math.cos(Math.toRadians(lat)));
        Map<String, Integer> candidatas = new LinkedHashMap<>();
        for (CuadraCoords c : coordsRepository.findByLatBetweenAndLngBetween(lat - dLat, lat + dLat, lng - dLng, lng + dLng)) {
            if (c.isApproximate() || c.getCuadra() != cuadra || !c.getLocalidad().equals(localidad)
                    || c.getCalleCanonica().equals(canonica)) continue;
            int distancia = (int) Math.round(metros(lat, lng, c.getLat(), c.getLng()));
            if (distancia > RADIO_MISMA_CALLE_M || !mismasPalabras(palabras, palabras(c.getCalleCanonica()))) continue;
            candidatas.merge(c.getCalleCanonica(), distancia, Math::min);
        }
        // Con dos candidatas no se sabe a cuál pertenece: no se adivina.
        if (candidatas.size() != 1) return canonica;
        String otra = candidatas.keySet().iterator().next();
        if (seContradicen(canonica, otra)) return canonica;

        // Queda el nombre con menos palabras; a igualdad, el que ya estaba.
        boolean quedaLaNueva = palabras.size() < palabras(otra).size();
        String queda = quedaLaNueva ? canonica : otra, seVa = quedaLaNueva ? otra : canonica;
        unir(seVa, queda, localidad, cuadra, candidatas.get(otra), ORIGEN_AL_APRENDER, false);
        return queda;
    }

    /**
     * Regla de forma (2026-10-07): el nombre con el que hay que guardar una cuadra cuando el que llega
     * es otra manera de escribir una calle que ya está ahí ("Avenida Camino del Perú" del teléfono y
     * "Camino del Perú" de la base), o null si no aplica. No mueve ni fusiona nada: solo evita abrir
     * una calle nueva. El lugar es el control, no un requisito exacto: alcanza con una cuadra de esa
     * calle con altura parecida (hasta {@value #FORMA_ALTURA}) a menos de {@value #FORMA_CERCA_M} m,
     * en cualquier localidad (hay calles que son el límite entre dos). No aplica si el nombre que
     * llega ya tiene cuadras propias en la zona (es otra calle: "Boulevard 9 de Julio" y "9 de
     * Julio") ni si hay dos candidatas.
     */
    private String mismaFormaCerca(String canonica, int cuadra, double lat, double lng) {
        String forma = CallesParecidas.forma(canonica);
        if (forma.isEmpty()) return null;
        double dLat = FORMA_CERCA_M / 111_320d;
        double dLng = FORMA_CERCA_M / (111_320d * Math.cos(Math.toRadians(lat)));
        Set<String> candidatas = new HashSet<>();
        for (CuadraCoords c : coordsRepository.findByLatBetweenAndLngBetween(lat - dLat, lat + dLat, lng - dLng, lng + dLng)) {
            if (c.isApproximate() || metros(lat, lng, c.getLat(), c.getLng()) > FORMA_CERCA_M) continue;
            if (c.getCalleCanonica().equals(canonica)) return null;
            if (Math.abs(c.getCuadra() - cuadra) <= FORMA_ALTURA && forma.equals(CallesParecidas.forma(c.getCalleCanonica()))) {
                candidatas.add(c.getCalleCanonica());
            }
        }
        return candidatas.size() == 1 ? candidatas.iterator().next() : null;
    }

    /**
     * Pares de nombres ya guardados que son la misma calle. Con {@code aplicar} los une; sin él solo
     * dice qué uniría (las filas movidas y fusionadas quedan en 0).
     */
    @Transactional
    public List<Union> duplicadas(boolean aplicar) {
        List<CuadraCoords> filas = coordsRepository.findAll().stream().filter(c -> !c.isApproximate()).toList();
        Map<String, List<CuadraCoords>> porLugar = new HashMap<>();
        Map<String, Integer> filasPorCalle = new HashMap<>();
        for (CuadraCoords c : filas) {
            porLugar.computeIfAbsent(c.getLocalidad() + "|" + c.getCuadra(), k -> new ArrayList<>()).add(c);
            filasPorCalle.merge(c.getCalleCanonica(), 1, Integer::sum);
        }
        // par de nombres (ordenado) -> la cuadra más cercana que los relaciona
        Map<String, CuadraCoords[]> pares = new LinkedHashMap<>();
        for (List<CuadraCoords> grupo : porLugar.values()) {
            for (int i = 0; i < grupo.size(); i++) {
                for (int j = i + 1; j < grupo.size(); j++) {
                    CuadraCoords a = grupo.get(i), b = grupo.get(j);
                    if (a.getCalleCanonica().equals(b.getCalleCanonica())) continue;
                    double d = metros(a.getLat(), a.getLng(), b.getLat(), b.getLng());
                    if (d > RADIO_MISMA_CALLE_M || !mismasPalabras(palabras(a.getCalleCanonica()), palabras(b.getCalleCanonica()))) continue;
                    String clave = a.getCalleCanonica().compareTo(b.getCalleCanonica()) < 0
                            ? a.getCalleCanonica() + "|" + b.getCalleCanonica() : b.getCalleCanonica() + "|" + a.getCalleCanonica();
                    CuadraCoords[] previo = pares.get(clave);
                    if (previo == null || d < metros(previo[0].getLat(), previo[0].getLng(), previo[1].getLat(), previo[1].getLng())) {
                        pares.put(clave, new CuadraCoords[]{a, b});
                    }
                }
            }
        }

        List<Union> uniones = new ArrayList<>();
        Map<String, String> ahoraSeLlama = new HashMap<>();
        for (CuadraCoords[] par : pares.values()) {
            String a = nombreActual(par[0].getCalleCanonica(), ahoraSeLlama), b = nombreActual(par[1].getCalleCanonica(), ahoraSeLlama);
            if (a.equals(b) || seContradicen(a, b)) continue;
            String queda = elegir(a, b, filasPorCalle), seVa = queda.equals(a) ? b : a;
            int distancia = (int) Math.round(metros(par[0].getLat(), par[0].getLng(), par[1].getLat(), par[1].getLng()));
            if (aplicar) {
                uniones.add(unir(seVa, queda, par[0].getLocalidad(), par[0].getCuadra(), distancia, ORIGEN_REVISION, false));
                ahoraSeLlama.put(seVa, queda);
                filasPorCalle.merge(queda, filasPorCalle.getOrDefault(seVa, 0), Integer::sum);
            } else {
                uniones.add(new Union(seVa, queda, par[0].getLocalidad(), par[0].getCuadra(), distancia, 0, 0));
                // Simulando también: si A se une a B, lo que después se uniría a A en realidad va a B.
                ahoraSeLlama.put(seVa, queda);
            }
        }
        return uniones;
    }

    @Transactional(readOnly = true)
    public List<CalleUnion> historial() {
        return unionRepository.findTop100ByOrderByCuandoDesc();
    }

    private static String nombreActual(String nombre, Map<String, String> ahoraSeLlama) {
        String actual = nombre;
        for (int i = 0; i < 10 && ahoraSeLlama.containsKey(actual); i++) actual = ahoraSeLlama.get(actual);
        return actual;
    }

    /** Menos palabras; a igualdad, la que tiene más cuadras aprendidas; a igualdad, el nombre más corto. */
    private static String elegir(String a, String b, Map<String, Integer> filasPorCalle) {
        int pa = palabras(a).size(), pb = palabras(b).size();
        if (pa != pb) return pa < pb ? a : b;
        int fa = filasPorCalle.getOrDefault(a, 0), fb = filasPorCalle.getOrDefault(b, 0);
        if (fa != fb) return fa > fb ? a : b;
        return a.length() <= b.length() ? a : b;
    }

    /**
     * Unión decidida desde "calles a revisar" (2026-10-07), con un link de Google Maps o a mano. Solo
     * pasan las cuadras de la zona (y las que les siguen en la misma localidad): el mismo nombre puede
     * ser otra calle en otra localidad ("Avenida Camino del Perú" de Tafí Viejo, a 7 km de la de San Miguel). Si quedan cuadras con el nombre
     * que se va, sus formas de escribirlo no se tocan.
     */
    @Transactional
    public Union unirPorRevision(String seVa, String queda, String localidad, int cuadra, int distanciaM) {
        return unir(seVa, queda, localidad, cuadra, distanciaM, ORIGEN_A_REVISAR, true);
    }

    /** Pasa lo de {@code seVa} a {@code queda}: las cuadras (todas, o solo las de la zona) y las formas de escribirla. */
    private Union unir(String seVa, String queda, String localidad, int cuadra, int distanciaM, String origen, boolean soloLaZona) {
        int movidas = 0, fusionadas = 0;
        List<FilaAntes> filasAntes = new ArrayList<>();
        List<AliasAntes> aliasAntes = new ArrayList<>();
        String aliasCreado = null;
        List<CuadraCoords> deLaQueQueda = soloLaZona ? coordsRepository.findByCalleCanonica(queda) : List.of();
        List<CuadraCoords> deLaQueSeVa = coordsRepository.findByCalleCanonica(seVa);
        // La zona avanza en cadena dentro de la localidad: una cuadra que pasa lleva a las que tiene cerca
        // (2026-10-07: "Boulevard 9 de Julio" quedó partida porque "9 de Julio" tenía una sola cuadra en la punta).
        Set<CuadraCoords> pasan = new HashSet<>();
        for (CuadraCoords fila : deLaQueSeVa) {
            if (deLaQueQueda.isEmpty() || deLaQueQueda.stream().anyMatch(q -> metros(fila.getLat(), fila.getLng(), q.getLat(), q.getLng()) <= ZONA_M)) pasan.add(fila);
        }
        for (boolean sumo = true; sumo; ) {
            sumo = false;
            for (CuadraCoords fila : deLaQueSeVa) {
                if (!pasan.contains(fila) && pasan.stream().anyMatch(p -> p.getLocalidad().equals(fila.getLocalidad())
                        && metros(fila.getLat(), fila.getLng(), p.getLat(), p.getLng()) <= ZONA_M)) sumo |= pasan.add(fila);
            }
        }
        for (CuadraCoords fila : deLaQueSeVa) {
            if (!pasan.contains(fila)) continue;
            Optional<CuadraCoords> yaEsta = coordsRepository.findByCalleCanonicaAndLocalidadAndCuadra(queda, fila.getLocalidad(), fila.getCuadra());
            if (yaEsta.isEmpty()) {
                filasAntes.add(antes(fila));
                fila.setCalleCanonica(queda);
                coordsRepository.save(fila);
                movidas++;
                continue;
            }
            // Las dos tenían esa cuadra: queda el punto de la fuente más confiable (el GPS del
            // cadete en la puerta antes que un buscador) y se suman las confirmaciones.
            CuadraCoords destino = yaEsta.get();
            filasAntes.add(antes(destino));
            filasAntes.add(antes(fila));
            if (!fila.isApproximate() && (destino.isApproximate()
                    || DireccionCacheService.confianza(fila.getProveedor()) > DireccionCacheService.confianza(destino.getProveedor()))) {
                destino.setLat(fila.getLat());
                destino.setLng(fila.getLng());
                destino.setProveedor(fila.getProveedor());
                destino.setMuestras(fila.getMuestras());
                destino.setCreadaEn(fila.getCreadaEn());
                destino.setApproximate(false);
            }
            destino.setConfirmaciones(destino.getConfirmaciones() + fila.getConfirmaciones());
            coordsRepository.save(destino);
            coordsRepository.delete(fila);
            fusionadas++;
        }
        boolean quedanCuadras = soloLaZona && !coordsRepository.findByCalleCanonica(seVa).isEmpty();
        boolean tieneAlias = false;
        for (DireccionAlias alias : quedanCuadras ? List.<DireccionAlias>of() : aliasRepository.findByCalleCanonica(seVa)) {
            aliasAntes.add(new AliasAntes(alias.getId(), alias.getCalleCanonica()));
            alias.setCalleCanonica(queda);
            aliasRepository.save(alias);
            tieneAlias |= alias.getVarianteNorm().equals(seVa);
        }
        // Que el nombre que se fue siga llevando a la calle: quien lo escriba la encuentra igual.
        if (!quedanCuadras && !tieneAlias && aliasRepository.findByVarianteNorm(seVa).isEmpty()) {
            DireccionAlias alias = new DireccionAlias();
            alias.setId(UUID.randomUUID().toString());
            alias.setVarianteNorm(seVa);
            alias.setLocalidad(localidad);
            alias.setCalleCanonica(queda);
            aliasRepository.save(alias);
            aliasCreado = alias.getId();
        }

        CalleUnion registro = new CalleUnion();
        registro.setId(UUID.randomUUID().toString());
        registro.setSeFue(seVa);
        registro.setQueda(queda);
        registro.setLocalidad(localidad);
        registro.setCuadra(cuadra);
        registro.setDistanciaM(distanciaM);
        registro.setFilasMovidas(movidas);
        registro.setFilasFusionadas(fusionadas);
        registro.setOrigen(origen);
        registro.setCuando(Instant.now());
        try {
            registro.setRespaldo(JSON.writeValueAsString(new Respaldo(filasAntes, aliasAntes, aliasCreado)));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            log.warn("No se pudo guardar el respaldo de la unión de \"{}\" con \"{}\": no se va a poder deshacer.", seVa, queda);
        }
        unionRepository.save(registro);
        log.info("Calles unidas ({}): \"{}\" pasa a ser \"{}\" (misma cuadra {} en {}, a {} m; {} cuadras movidas, {} fusionadas).",
                origen, seVa, queda, cuadra, localidad, distanciaM, movidas, fusionadas);
        return new Union(seVa, queda, localidad, cuadra, distanciaM, movidas, fusionadas);
    }

    /**
     * Deshace una unión (2026-10-07): cada cuadra y cada alias que tocó vuelve a como estaba. Lo que
     * se aprendió después con el nombre que quedó no se toca.
     */
    @Transactional
    public CalleUnion deshacer(String unionId) {
        CalleUnion union = unionRepository.findById(unionId).orElseThrow(() -> new ResourceNotFoundException("Esa unión no existe."));
        if (!union.isSePuedeDeshacer()) {
            throw new BadRequestException("Esa unión no se puede deshacer: ya se deshizo o es anterior a que se guardara cómo estaba todo.");
        }
        Respaldo respaldo;
        try {
            respaldo = JSON.readValue(union.getRespaldo(), Respaldo.class);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new BadRequestException("No se pudo leer cómo estaba todo antes de esa unión.");
        }
        for (FilaAntes f : respaldo.filas()) {
            CuadraCoords c = coordsRepository.findById(f.id()).orElseGet(CuadraCoords::new);
            c.setId(f.id());
            c.setCalleCanonica(f.calle());
            c.setLocalidad(f.localidad());
            c.setCuadra(f.cuadra());
            c.setLat(f.lat());
            c.setLng(f.lng());
            c.setApproximate(f.approximate());
            c.setProveedor(f.proveedor());
            c.setConfirmaciones(f.confirmaciones());
            c.setMuestras(f.muestras());
            c.setCreadaEn(Instant.ofEpochMilli(f.creadaEn()));
            coordsRepository.save(c);
        }
        for (AliasAntes a : respaldo.alias()) {
            aliasRepository.findById(a.id()).ifPresent(alias -> {
                alias.setCalleCanonica(a.calle());
                aliasRepository.save(alias);
            });
        }
        if (respaldo.aliasCreado() != null) aliasRepository.deleteById(respaldo.aliasCreado());
        union.setDeshechaEn(Instant.now());
        unionRepository.save(union);
        log.info("Unión deshecha: \"{}\" vuelve a ser una calle aparte de \"{}\" ({} cuadras restauradas).",
                union.getSeFue(), union.getQueda(), respaldo.filas().size());
        return union;
    }

    private static FilaAntes antes(CuadraCoords c) {
        return new FilaAntes(c.getId(), c.getCalleCanonica(), c.getLocalidad(), c.getCuadra(), c.getLat(), c.getLng(), c.isApproximate(),
                c.getProveedor(), c.getConfirmaciones(), c.getMuestras(), c.getCreadaEn().toEpochMilli());
    }

    /** True si en alguna cuadra de la misma localidad los dos nombres están lejos: son calles distintas. */
    private boolean seContradicen(String a, String b) {
        Map<String, CuadraCoords> deA = new HashMap<>();
        for (CuadraCoords c : coordsRepository.findByCalleCanonica(a)) {
            if (!c.isApproximate()) deA.put(c.getLocalidad() + "|" + c.getCuadra(), c);
        }
        for (CuadraCoords c : coordsRepository.findByCalleCanonica(b)) {
            CuadraCoords par = c.isApproximate() ? null : deA.get(c.getLocalidad() + "|" + c.getCuadra());
            if (par != null && metros(par.getLat(), par.getLng(), c.getLat(), c.getLng()) > CONTRADICCION_M) return true;
        }
        return false;
    }

    private static Set<String> palabras(String calle) {
        return new HashSet<>(CallesParecidas.palabrasClave(calle));
    }

    /** Sin palabras propias, o una sola de menos de 4 letras ("paz"): no alcanza para decir que es la misma. */
    private static boolean noDistingue(Set<String> palabras) {
        return palabras.isEmpty() || (palabras.size() == 1 && palabras.iterator().next().length() < 4);
    }

    /** Un nombre contiene al otro (o son iguales): "suipacha" y "batalla de suipacha", "mitre" y "avenida mitre". */
    static boolean mismasPalabras(Set<String> a, Set<String> b) {
        if (noDistingue(a) || noDistingue(b)) return false;
        return a.containsAll(b) || b.containsAll(a);
    }

    private static double metros(double lat1, double lng1, double lat2, double lng2) {
        return GeocodingService.distanciaKm(lat1, lng1, lat2, lng2) * 1000;
    }
}
