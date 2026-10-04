package com.cadeteria.backend.service;

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
    public static final String ORIGEN_AL_APRENDER = "al aprender";
    public static final String ORIGEN_REVISION = "revisión de duplicadas";

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
        unir(seVa, queda, localidad, cuadra, candidatas.get(otra), ORIGEN_AL_APRENDER);
        return queda;
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
                uniones.add(unir(seVa, queda, par[0].getLocalidad(), par[0].getCuadra(), distancia, ORIGEN_REVISION));
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

    /** Pasa todo lo de {@code seVa} a {@code queda}: las cuadras y las formas de escribirla. */
    private Union unir(String seVa, String queda, String localidad, int cuadra, int distanciaM, String origen) {
        int movidas = 0, fusionadas = 0;
        for (CuadraCoords fila : coordsRepository.findByCalleCanonica(seVa)) {
            Optional<CuadraCoords> yaEsta = coordsRepository.findByCalleCanonicaAndLocalidadAndCuadra(queda, fila.getLocalidad(), fila.getCuadra());
            if (yaEsta.isEmpty()) {
                fila.setCalleCanonica(queda);
                coordsRepository.save(fila);
                movidas++;
                continue;
            }
            // Las dos tenían esa cuadra: queda el punto de la fuente más confiable (el GPS del
            // cadete en la puerta antes que un buscador) y se suman las confirmaciones.
            CuadraCoords destino = yaEsta.get();
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
        boolean tieneAlias = false;
        for (DireccionAlias alias : aliasRepository.findByCalleCanonica(seVa)) {
            alias.setCalleCanonica(queda);
            aliasRepository.save(alias);
            tieneAlias |= alias.getVarianteNorm().equals(seVa);
        }
        // Que el nombre que se fue siga llevando a la calle: quien lo escriba la encuentra igual.
        if (!tieneAlias && aliasRepository.findByVarianteNorm(seVa).isEmpty()) {
            DireccionAlias alias = new DireccionAlias();
            alias.setId(UUID.randomUUID().toString());
            alias.setVarianteNorm(seVa);
            alias.setLocalidad(localidad);
            alias.setCalleCanonica(queda);
            aliasRepository.save(alias);
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
        unionRepository.save(registro);
        log.info("Calles unidas ({}): \"{}\" pasa a ser \"{}\" (misma cuadra {} en {}, a {} m; {} cuadras movidas, {} fusionadas).",
                origen, seVa, queda, cuadra, localidad, distanciaM, movidas, fusionadas);
        return new Union(seVa, queda, localidad, cuadra, distanciaM, movidas, fusionadas);
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
