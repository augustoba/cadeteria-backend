package com.cadeteria.backend.service;

import com.cadeteria.backend.config.AppProperties;
import com.cadeteria.backend.model.CuadraCoords;
import com.cadeteria.backend.util.DireccionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Proxy server-side del buscador de direcciones — punto único de geocoding para todo lo que
 * lo necesite: la página pública "/pedir" (mejora 2026-09-17, pedida por el dueño: "si hacen
 * muchas búsquedas juntas se puede bloquear?") y, desde la Fase 1 de
 * spec-geocoding-cache.md (2026-09-21), también el panel admin (antes llamaba directo a los
 * proveedores desde el navegador vía `geocoding.service.ts`, con las keys expuestas en el
 * bundle). Pasando la búsqueda por acá, {@link com.cadeteria.backend.config.RateLimitFilter}
 * limita por IP a "/pedir", y {@link DireccionCacheService} evita pagar dos veces el mismo
 * geocode sin importar por qué pantalla entró.
 * <p>
 * Fuentes: Nominatim (siempre, sin key), Geoapify y LocationIQ (si hay key cargada) — Photon
 * quedó afuera (contingencia de bajo impacto según el comentario original del front: "rara vez
 * suma cobertura nueva"), no hacía falta duplicar las 4 fuentes del front en el backend.
 * <p>
 * Geoapify y LocationIQ admiten VARIAS keys (varias cuentas gratuitas propias) separadas por
 * coma o salto de línea en Configuración ("geoapify_keys"/"locationiq_keys") —
 * {@link ApiKeyPoolService} rota a la siguiente apenas una se queda sin cupo (HTTP 429/403). Si
 * no hay ninguna cargada en Configuración, se usa la de application.yml (env var
 * GEOAPIFY_KEY/LOCATIONIQ_KEY) como valor legado.
 */
@Service
public class GeocodingProxyService {

    private static final Logger log = LoggerFactory.getLogger(GeocodingProxyService.class);

    public record GeoAddress(String label, String street, Integer number, String locality,
                              double lat, double lng, boolean approximate, String proveedor) {}

    private static final String NOMINATIM_URL = "https://nominatim.openstreetmap.org/search";
    private static final String NOMINATIM_REVERSE_URL = "https://nominatim.openstreetmap.org/reverse";
    /** Nominatim exige identificar al cliente (User-Agent propio) — sin esto responde 403 siempre,
     *  ver https://operations.osmfoundation.org/policies/nominatim/. */
    private static final String NOMINATIM_USER_AGENT = "CadeteriaBackend/1.0 (+https://github.com/augustoba/cadeteria-backend)";
    private static final String GEOAPIFY_URL = "https://api.geoapify.com/v1/geocode/search";
    private static final String LOCATIONIQ_URL = "https://us1.locationiq.com/v1/search";
    private static final String GOOGLE_URL = "https://maps.googleapis.com/maps/api/geocode/json";
    /** sw|ne de la provincia de Tucumán en el formato de "bounds" de Google (lat,lng). */
    private static final String TUCUMAN_BOUNDS_GOOGLE = "-28.1,-66.2|-26.0,-64.4";
    public static final String PROVEEDOR_GEOAPIFY = "geoapify";
    public static final String PROVEEDOR_LOCATIONIQ = "locationiq";
    public static final String PROVEEDOR_NOMINATIM = "nominatim";
    public static final String PROVEEDOR_CACHE = "cache";
    public static final String PROVEEDOR_GOOGLE = "google";
    /** Keys de Google Geocoding (Configuración). Vacío = la búsqueda ampliada usa solo los gratuitos. */
    public static final String CONFIG_GOOGLE_KEYS = "google_geocoding_keys";
    public static final String CONFIG_GEOAPIFY_KEYS = "geoapify_keys";
    public static final String CONFIG_LOCATIONIQ_KEYS = "locationiq_keys";
    private static final int MAX_INTENTOS_POR_PROVEEDOR = 10;
    /** left,top,right,bottom de la provincia de Tucumán, para sesgar la búsqueda. */
    private static final String TUCUMAN_VIEWBOX = "-66.2,-26.0,-64.4,-28.1";
    private static final Pattern NUMERO_FINAL = Pattern.compile("^(.+?)[\\s,]*(\\d{1,6})\\s*$");

    /** Precisión máxima (metros) del GPS del cadete para aprender una dirección de Retirado/Entregado. */
    public static final String CONFIG_GPS_PRECISION_MAX = "aprender_gps_precision_max_m";
    /** Precisión máxima (metros) del GPS para aprender la calle que resolvió el teléfono mientras anda. */
    public static final String CONFIG_GEOCODER_PRECISION_MAX = "aprender_geocoder_precision_max_m";
    /**
     * Si el buscador sale a preguntar a los servicios gratuitos de afuera (Nominatim, Geoapify,
     * LocationIQ) cuando la base propia no tiene la dirección (2026-10-03). Apagado, busca solo en
     * la base propia y lo que falte se carga pegando el link de Google Maps. El "qué calle hay acá"
     * (reverse) no depende de esto.
     */
    public static final String CONFIG_BUSQUEDA_EXTERNA = "busqueda_externa_activa";
    /** Tope diario de reverse geocoding de respaldo (LocationIQ/Geoapify) cuando Nominatim no da la altura. 0 = apagado. */
    public static final String CONFIG_REVERSE_RESPALDO_MAX_DIA = "reverse_respaldo_max_dia";
    /** El GPS del cadete tiene que caer a menos de esto del pin del pedido (si no, marcó en otro lado). */
    private static final double DISTANCIA_MAX_AL_PIN_M = 2000;
    /**
     * Sin que el mapa ni el teléfono confirmen la calle, el GPS del cadete corrige un pin solo si
     * marcó a menos de esto (el radio en que la app lo deja marcar Retirado/Entregado, 2026-09-28).
     */
    private static final double DISTANCIA_CADETE_CORRIGE_PIN_M = 150;
    /** Radio para contestar "qué calle hay acá" con un punto propio en vez de preguntarle a OSM. */
    static final int RADIO_CALLE_PROPIA_M = 30;
    private static final String LOCATIONIQ_REVERSE_URL = "https://us1.locationiq.com/v1/reverse";
    private static final String GEOAPIFY_REVERSE_URL = "https://api.geoapify.com/v1/geocode/reverse";

    private final RestClient restClient = RestClient.create();
    private final ApiKeyPoolService apiKeyPool;
    private final DireccionCacheService direccionCache;
    private final ConfiguracionService configuracionService;
    /** Contador del respaldo del día (se reinicia al cambiar de día o al reiniciar el backend). */
    private LocalDate diaRespaldo = LocalDate.now();
    private int usosRespaldoHoy = 0;
    private final String geoapifyKeyLegado;
    private final String locationIqKeyLegado;

    /** Último resultado real de Nominatim — lo lee el panel de salud (ver SaludController), no hace un ping aparte. */
    private volatile boolean nominatimOk = true;

    public GeocodingProxyService(AppProperties props, ApiKeyPoolService apiKeyPool, DireccionCacheService direccionCache,
                                 ConfiguracionService configuracionService) {
        this.apiKeyPool = apiKeyPool;
        this.direccionCache = direccionCache;
        this.configuracionService = configuracionService;
        this.geoapifyKeyLegado = props.getMaps().getGeoapifyKey();
        this.locationIqKeyLegado = props.getMaps().getLocationIqKey();
    }

    public boolean isNominatimOk() {
        return nominatimOk;
    }

    public List<GeoAddress> buscar(String textoCrudo) {
        String q = textoCrudo == null ? "" : textoCrudo.trim().replaceAll("\\s+", " ");
        if (q.length() < 4) return List.of();

        Matcher m = NUMERO_FINAL.matcher(q);
        boolean tieneNumero = m.matches();
        String streetPart = tieneNumero ? m.group(1).trim() : q;
        Integer numero = tieneNumero ? Integer.parseInt(m.group(2)) : null;

        // La calle conocida de la que se sacan las cuadras vecinas si esta cuadra no está aprendida.
        boolean calleConocida = direccionCache.conoceCalle(streetPart);
        String canonicaConocida = calleConocida ? direccionCache.canonicalizar(streetPart) : null;
        // 2026-10-03: las otras calles que se dicen con esas palabras ("mitre" / "avenida bartolome
        // mitre" / "av mitre", "suipacha" / "batalla de suipacha", el pasaje sin "pasaje") y las que
        // antes se llamaban así ("rivadavia" -> Virgen de la Merced). No se adivina cuál es: se ofrecen.
        Set<String> otras = otrasCalles(streetPart, canonicaConocida);

        if (numero != null) {
            // En todas las localidades donde esté ("Belgrano 500" en San Miguel y en Yerba Buena se
            // ofrecen las dos); antes con más de una se trataba como desconocida.
            List<GeoAddress> propias = desdeCache(direccionCache.buscarOpciones(streetPart, numero), numero);
            if (!propias.isEmpty()) {
                List<GeoAddress> todas = new ArrayList<>(propias);
                for (String canonica : otras) todas.addAll(desdeCache(direccionCache.mirarOpciones(canonica, numero), numero));
                return sinRepetir(todas);
            }
        }
        // Cuadras estimadas con las vecinas ("sin altura exacta"): van al final, si no aparece nada mejor.
        List<GeoAddress> estimadasPropias = new ArrayList<>();

        // 3h (2026-09-28): "colom 4600" no es ninguna calle, pero la cache conoce "colombia". Con una
        // sola calle que empiece así se completa el nombre — también antes de preguntar afuera, que
        // con "colom" no encuentran nada. Con varias ("sant" -> Santiago, Santa Fe...) no se adivina:
        // se ofrecen las que ya tienen esa cuadra aprendida. 2026-10-03: si ninguna empieza así, las
        // que se le parecen ("belgarno", "bolibar"), con la misma regla. Nada de esto si ya hay
        // calles con esas palabras: "lopez" es el Pasaje Belisario López antes que "López Mañán".
        if (!calleConocida && otras.isEmpty()) {
            List<String> candidatas = direccionCache.callesQueEmpiezanCon(streetPart);
            if (candidatas.isEmpty()) candidatas = direccionCache.callesParecidas(streetPart);
            if (candidatas.size() == 1) {
                canonicaConocida = candidatas.get(0);
                streetPart = DireccionUtils.nombreParaMostrar(candidatas.get(0));
                q = numero != null ? streetPart + " " + numero : streetPart;
                if (numero != null) {
                    List<GeoAddress> propias = desdeCache(direccionCache.opcionesPorCanonica(candidatas.get(0), numero), numero);
                    if (!propias.isEmpty()) return propias;
                }
            } else if (candidatas.size() > 1 && numero != null) {
                List<GeoAddress> opciones = new ArrayList<>();
                for (String canonica : candidatas) {
                    opciones.addAll(desdeCache(direccionCache.mirarOpciones(canonica, numero), numero));
                }
                if (!opciones.isEmpty()) return opciones;
                for (String canonica : candidatas) estimadasPropias.addAll(estimadas(canonica, numero));
            }
        }

        // Las otras calles que ya tienen esa cuadra van primero, con su nombre completo; no reemplazan
        // a los buscadores porque puede ser otra calle ("peru" no es "camino del peru") y el que elige
        // es quien carga.
        List<GeoAddress> parecidas = new ArrayList<>();
        if (numero != null) {
            // La calle es conocida pero no esa cuadra: se estima con las vecinas, antes que las de las otras.
            if (canonicaConocida != null) estimadasPropias.addAll(0, estimadas(canonicaConocida, numero));
            for (String canonica : otras) {
                List<GeoAddress> exactas = desdeCache(direccionCache.mirarOpciones(canonica, numero), numero);
                if (exactas.isEmpty()) estimadasPropias.addAll(estimadas(canonica, numero));
                else parecidas.addAll(exactas);
            }
        }

        if (!busquedaExternaActiva()) {
            parecidas.addAll(estimadasPropias);
            return sinRepetir(parecidas);
        }

        List<GeoAddress> out = buscarAfuera(q, streetPart, numero);
        if (numero != null && !out.isEmpty() && !out.get(0).approximate() && esLaCalleTipeada(streetPart, out.get(0).street())) {
            GeoAddress mejor = out.get(0);
            direccionCache.guardar(streetPart, numero, mejor.street(), mejor.locality(),
                    mejor.lat(), mejor.lng(), mejor.approximate(), mejor.proveedor());
        }
        parecidas.addAll(out);
        // La estimación propia va al final, y solo si afuera tampoco dieron con la altura exacta.
        if (out.stream().allMatch(GeoAddress::approximate)) parecidas.addAll(estimadasPropias);
        return sinRepetir(parecidas);
    }

    /** Calles que antes se llamaban como lo tipeado y las que se dicen con las mismas palabras, sin la propia. */
    private Set<String> otrasCalles(String streetPart, String propia) {
        Set<String> otras = new java.util.LinkedHashSet<>(direccionCache.callesConNombreAnterior(streetPart));
        otras.addAll(direccionCache.callesPorPalabras(streetPart));
        otras.remove(propia);
        return otras;
    }

    /** La misma dirección puede llegar por dos caminos (el nombre anterior y las palabras): queda la primera. */
    private static List<GeoAddress> sinRepetir(List<GeoAddress> resultados) {
        Set<String> vistas = new HashSet<>();
        List<GeoAddress> out = new ArrayList<>();
        for (GeoAddress a : resultados) {
            if (vistas.add(a.label().toLowerCase())) out.add(a);
        }
        return out;
    }

    private static final int MAX_SUGERENCIAS_DE_CALLE = 8;

    /**
     * Nombres de calles conocidas para lo que se viene escribiendo SIN la altura (2026-10-03): la base
     * propia responde por calle + cuadra, así que con "alem" sola no hay nada que ubicar. El panel
     * los muestra para completar ("Avenida Alem") y que solo falte escribir el número.
     */
    public List<String> sugerirCalles(String textoCrudo) {
        String q = textoCrudo == null ? "" : textoCrudo.trim().replaceAll("\\s+", " ");
        if (q.length() < 4 || NUMERO_FINAL.matcher(q).matches()) return List.of();
        Set<String> canonicas = new java.util.LinkedHashSet<>();
        if (direccionCache.conoceCalle(q)) canonicas.add(direccionCache.canonicalizar(q));
        canonicas.addAll(direccionCache.callesConNombreAnterior(q));
        canonicas.addAll(direccionCache.callesPorPalabras(q));
        canonicas.addAll(direccionCache.callesQueEmpiezanCon(q));
        if (canonicas.isEmpty()) canonicas.addAll(direccionCache.callesParecidas(q));
        return canonicas.stream().filter(java.util.Objects::nonNull).limit(MAX_SUGERENCIAS_DE_CALLE)
                .map(DireccionUtils::nombreParaMostrar).toList();
    }

    /** Los buscadores gratuitos, con el texto completo y con la calle sola (+ la altura tipeada). */
    List<GeoAddress> buscarAfuera(String q, String streetPart, Integer numero) {
        List<GeoAddress> resultados = new ArrayList<>();
        resultados.addAll(queryNominatim(q, numero));
        resultados.addAll(queryGeoapify(q, numero));
        resultados.addAll(queryLocationIq(q, numero));
        if (numero != null && streetPart.length() >= 3) {
            queryNominatim(streetPart, null).forEach(r -> resultados.add(conNumero(r, numero)));
            queryGeoapify(streetPart, null).forEach(r -> resultados.add(conNumero(r, numero)));
            queryLocationIq(streetPart, null).forEach(r -> resultados.add(conNumero(r, numero)));
        }
        return dedupe(resultados);
    }

    /**
     * Un acierto de la cache como resultado del buscador. Una entrada aproximada (sin la altura
     * exacta) no sirve como respuesta única: el pin queda en cualquier punto de la calle y hasta con
     * la localidad equivocada (bug del 2026-09-24: "Colombia 4695" devolvía solo Yerba Buena).
     */
    private GeoAddress desdeCache(DireccionCacheService.ResultadoCache cacheado, int numero) {
        if (cacheado == null || cacheado.approximate()) return null;
        return comoResultado(cacheado, numero);
    }

    private List<GeoAddress> desdeCache(List<DireccionCacheService.ResultadoCache> cacheados, int numero) {
        if (cacheados == null) return List.of();
        return cacheados.stream().map(c -> desdeCache(c, numero)).filter(java.util.Objects::nonNull).toList();
    }

    /** Cuadras estimadas con las vecinas de la base propia: se ofrecen como "sin altura exacta". */
    private List<GeoAddress> estimadas(String canonica, int numero) {
        List<DireccionCacheService.ResultadoCache> estimadas = direccionCache.estimar(canonica, numero);
        return estimadas == null ? List.of() : estimadas.stream().map(c -> comoResultado(c, numero)).toList();
    }

    private static GeoAddress comoResultado(DireccionCacheService.ResultadoCache cacheado, int numero) {
        String calle = DireccionUtils.nombreParaMostrar(cacheado.calleCanonica());
        String base = calle + " " + numero;
        String label = cacheado.localidad() != null && !cacheado.localidad().isBlank()
                ? base + ", " + cacheado.localidad() : base;
        return new GeoAddress(label, calle, numero, cacheado.localidad(),
                cacheado.lat(), cacheado.lng(), cacheado.approximate(), PROVEEDOR_CACHE);
    }

    /** Ver {@link #CONFIG_BUSQUEDA_EXTERNA}. Prendida por defecto: es como venía funcionando. */
    private boolean busquedaExternaActiva() {
        return configuracionService.getBoolean(CONFIG_BUSQUEDA_EXTERNA, true);
    }

    /**
     * "No está mi dirección — buscar de nuevo" (2026-09-24): segundo intento cuando la lista de
     * {@link #buscar} no trae la dirección correcta. Saltea la cache (un resultado cacheado mal
     * hacía que siempre apareciera solo ese) y prueba Google si hay key cargada; sin key, vuelve
     * a consultar los gratuitos directo. Si tampoco aparece, el front ofrece ubicarla a mano.
     *
     * <p>El mejor resultado de Google (con altura exacta) se guarda en la cache marcado
     * {@code google}: sus condiciones permiten guardarlo hasta 30 días, y la cache lo ignora y
     * lo borra pasados {@code google_cache_dias} (ver {@link DireccionCacheService}).
     */
    public List<GeoAddress> buscarAmpliado(String textoCrudo) {
        String q = textoCrudo == null ? "" : textoCrudo.trim().replaceAll("\\s+", " ");
        if (q.length() < 4) return List.of();
        Matcher m = NUMERO_FINAL.matcher(q);
        boolean tieneNumero = m.matches();
        Integer numero = tieneNumero ? Integer.parseInt(m.group(2)) : null;

        List<GeoAddress> google = queryGoogle(q, numero);
        if (!google.isEmpty()) {
            List<GeoAddress> out = dedupe(google);
            GeoAddress mejor = out.get(0);
            if (numero != null && !mejor.approximate() && esLaCalleTipeada(m.group(1).trim(), mejor.street())) {
                direccionCache.guardar(m.group(1).trim(), numero, mejor.street(), mejor.locality(),
                        mejor.lat(), mejor.lng(), false, PROVEEDOR_GOOGLE);
            }
            return out;
        }

        // Con la búsqueda externa apagada no hay segundo intento gratuito: queda el link o ubicarla a mano.
        if (!busquedaExternaActiva()) return List.of();

        List<GeoAddress> resultados = new ArrayList<>();
        resultados.addAll(queryNominatim(q, numero));
        resultados.addAll(queryGeoapify(q, numero));
        resultados.addAll(queryLocationIq(q, numero));
        return dedupe(resultados);
    }

    private List<GeoAddress> queryGoogle(String texto, Integer numeroEsperado) {
        for (int intento = 0; intento < MAX_INTENTOS_POR_PROVEEDOR; intento++) {
            String key = apiKeyPool.siguienteClave(PROVEEDOR_GOOGLE, CONFIG_GOOGLE_KEYS);
            if (key == null || key.isBlank()) return List.of();
            String url = GOOGLE_URL + "?language=es&region=ar&bounds=" + encode(TUCUMAN_BOUNDS_GOOGLE)
                    + "&components=" + encode("country:AR|administrative_area:Tucumán")
                    + "&address=" + encode(texto + ", Tucumán") + "&key=" + key;
            try {
                apiKeyPool.registrarUso(PROVEEDOR_GOOGLE, CONFIG_GOOGLE_KEYS, key);
                Map<String, Object> data = restClient.get().uri(java.net.URI.create(url))
                        .retrieve()
                        .body(new ParameterizedTypeReference<Map<String, Object>>() {});
                if (data == null) return List.of();
                String status = String.valueOf(data.get("status"));
                if ("OVER_QUERY_LIMIT".equals(status) || "OVER_DAILY_LIMIT".equals(status) || "REQUEST_DENIED".equals(status)) {
                    log.warn("Google Geocoding respondió {} — se pasa a la siguiente key.", status);
                    apiKeyPool.marcarAgotada(PROVEEDOR_GOOGLE, CONFIG_GOOGLE_KEYS, key);
                    continue;
                }
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> results = (List<Map<String, Object>>) data.get("results");
                if (results == null) return List.of();
                List<GeoAddress> out = new ArrayList<>();
                for (Map<String, Object> r : results) {
                    GeoAddress a = desdeGoogle(r, numeroEsperado);
                    if (a != null) out.add(a);
                }
                return out;
            } catch (Exception e) {
                log.warn("Fallo la búsqueda en Google de \"{}\": {}", texto, e.getMessage());
                return List.of();
            }
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private GeoAddress desdeGoogle(Map<String, Object> r, Integer numeroEsperado) {
        List<Map<String, Object>> componentes = (List<Map<String, Object>>) r.get("address_components");
        Map<String, Object> geometry = (Map<String, Object>) r.get("geometry");
        if (componentes == null || geometry == null) return null;
        String calle = "", altura = null, localidad = "", provincia = "";
        for (Map<String, Object> c : componentes) {
            List<String> tipos = (List<String>) c.get("types");
            String nombre = String.valueOf(c.get("long_name"));
            if (tipos == null) continue;
            if (tipos.contains("route")) calle = nombre;
            else if (tipos.contains("street_number")) altura = nombre;
            else if (tipos.contains("locality")) localidad = nombre;
            else if (tipos.contains("administrative_area_level_1")) provincia = nombre;
        }
        if (calle.isBlank() || !provincia.toLowerCase().contains("tucum")) return null;
        Map<String, Object> loc = (Map<String, Object>) geometry.get("location");
        if (loc == null) return null;
        // ROOFTOP / RANGE_INTERPOLATED = ubicó la altura; GEOMETRIC_CENTER / APPROXIMATE = solo la calle o zona.
        String tipo = String.valueOf(geometry.get("location_type"));
        boolean aproximada = altura == null || !("ROOFTOP".equals(tipo) || "RANGE_INTERPOLATED".equals(tipo));
        Integer numero = parseNumero(altura, numeroEsperado);
        String base = numero != null ? calle + " " + numero : calle;
        String label = localidad.isBlank() ? base : base + ", " + localidad;
        return new GeoAddress(label, calle, numero, localidad,
                ((Number) loc.get("lat")).doubleValue(), ((Number) loc.get("lng")).doubleValue(), aproximada, PROVEEDOR_GOOGLE);
    }

    /**
     * Pin que el admin (o el cliente, ya revisado por el admin) ubicó a mano o sacó de un link de
     * Google Maps, confirmado con el pedido: se aprende "calle tipeada + altura -> ese punto" para
     * que la próxima vez salga directo de la cache. La calle canónica es la del reverse de ese
     * punto (spec §5.4: nunca una adivinada), y solo si se parece a la que se tipeó — si no, el pin
     * quedó en la esquina o en otra calle y no conviene aprenderlo. Async: el reverse tarda y el
     * pedido no tiene por qué esperarlo.
     */
    @Async
    public void aprenderPin(String direccion, Double lat, Double lng, String fuente) {
        if (!DireccionCacheService.PROVEEDOR_MANUAL.equals(fuente)
                && !DireccionCacheService.PROVEEDOR_GOOGLE_LINK.equals(fuente)) return;
        aprender(direccion, lat, lng, fuente);
    }

    /**
     * Pin que puso un cliente en /pedir, al aprobar la solicitud (3j, 2026-09-28): cualquiera puede
     * inventar un pin, así que entra siempre como sin confirmar (aunque el mapa confirme la calle, la
     * altura puede estar mal) y lo confirma o corrige el primer cadete que marque ahí.
     */
    @Async
    public void aprenderPinDeCliente(String direccion, Double lat, Double lng, String fuente) {
        if (!DireccionCacheService.PROVEEDOR_MANUAL.equals(fuente)
                && !DireccionCacheService.PROVEEDOR_GOOGLE_LINK.equals(fuente)) return;
        aprender(direccion, lat, lng, DireccionCacheService.PROVEEDOR_MANUAL_SIN_CONFIRMAR);
    }

    /**
     * El cadete marcó Retirado/Entregado parado en la puerta (2026-09-26): la dirección escrita en el
     * pedido + el GPS del teléfono en ese momento es la mejor fuente para la cache — justo en las
     * direcciones que usan los clientes y en las calles donde los buscadores gratuitos no tienen
     * alturas. Solo si el GPS es preciso (≤ {@code aprender_gps_precision_max_m}, default 50 m) y cae
     * cerca del pin del pedido (si no, marcó desde otro lado); la calle se confirma con el reverse
     * igual que un pin manual.
     */
    /**
     * El teléfono del cadete resolvió calle y altura de su posición con su Geocoder (Android, datos
     * de Google) mientras anda (2026-09-26). Se guarda como {@code android_geocoder} — vence como lo
     * de Google — solo con buena precisión (≤ {@code aprender_geocoder_precision_max_m}, default 30 m):
     * con un punto impreciso el Geocoder devuelve la cuadra o la calle de al lado.
     *
     * @return true si la guardó (el mapeo de calles no tiene que volver a consultar ese punto).
     */
    public boolean aprenderDelTelefono(String calle, Integer altura, String localidad, double lat, double lng, Float precisionM) {
        if (calle == null || calle.isBlank() || altura == null || altura <= 0 || precisionM == null) return false;
        int maxPrecision = configuracionService.getInt(CONFIG_GEOCODER_PRECISION_MAX, 30);
        if (maxPrecision <= 0 || precisionM > maxPrecision) return false;
        String canonica = DireccionUtils.expandirAbreviaturas(calle.trim());
        direccionCache.guardar(calle.trim(), altura, canonica, limpiarLocalidad(localidad == null ? "" : localidad),
                lat, lng, false, DireccionCacheService.PROVEEDOR_ANDROID_GEOCODER);
        return true;
    }

    @Async
    public void aprenderDeCadete(String direccion, Double pinLat, Double pinLng, Double lat, Double lng, Float precisionM) {
        aprenderDeCadete(direccion, pinLat, pinLng, lat, lng, precisionM, null, null);
    }

    /**
     * Igual, con la calle que resolvió el teléfono en ese momento (2026-09-26): si el reverse no
     * confirma la calle (OSM no la conoce o la tiene con otro nombre) pero el teléfono sí, se aprende
     * igual con el nombre del teléfono.
     */
    @Async
    public void aprenderDeCadete(String direccion, Double pinLat, Double pinLng, Double lat, Double lng, Float precisionM,
                                 String calleTelefono, String localidadTelefono) {
        if (lat == null || lng == null || precisionM == null) return;
        int maxPrecision = configuracionService.getInt(CONFIG_GPS_PRECISION_MAX, 50);
        if (maxPrecision <= 0 || precisionM > maxPrecision) return;
        if (pinLat != null && pinLng != null
                && GeocodingService.distanciaKm(pinLat, pinLng, lat, lng) * 1000 > DISTANCIA_MAX_AL_PIN_M) {
            log.info("No se aprende \"{}\" del GPS del cadete: marcó a más de {} m del pin.", direccion, (int) DISTANCIA_MAX_AL_PIN_M);
            return;
        }
        aprender(direccion, lat, lng, DireccionCacheService.PROVEEDOR_CADETE_GPS, calleTelefono, localidadTelefono);
    }

    private void aprender(String direccion, Double lat, Double lng, String fuente) {
        aprender(direccion, lat, lng, fuente, null, null);
    }

    private void aprender(String direccion, Double lat, Double lng, String fuente, String calleTelefono, String localidadTelefono) {
        if (direccion == null || lat == null || lng == null) return;
        // "Colombia 4695, San Miguel de Tucumán" -> "Colombia" + 4695
        Matcher m = NUMERO_FINAL.matcher(direccion.split(",")[0].trim());
        if (!m.matches()) return;
        String calleTipeada = m.group(1).trim();
        int numero = Integer.parseInt(m.group(2));
        // 3j (2026-09-28): si para esta dirección ya hay un pin puesto por una persona, el GPS del
        // cadete parado en la puerta lo confirma o lo corrige — en la MISMA fila (misma calle y
        // localidad), así no queda una segunda fila que haga ambigua la cuadra.
        CuadraCoords pinPrevio = null;
        if (DireccionCacheService.PROVEEDOR_CADETE_GPS.equals(fuente)) {
            CuadraCoords fila = direccionCache.filaDe(calleTipeada, numero);
            if (fila != null && DireccionCacheService.esPin(fila.getProveedor())) pinPrevio = fila;
        }
        GeoAddress r = reverse(lat, lng);
        if (r != null && esLaCalleTipeada(calleTipeada, r.street())) {
            if (pinPrevio != null) confirmarPin(pinPrevio, calleTipeada, numero, lat, lng, "el mapa confirma la calle");
            else direccionCache.guardar(calleTipeada, numero, r.street(), r.locality(), lat, lng, false, fuente);
            return;
        }
        // El reverse (datos de OSM) no la confirma, pero el teléfono sí: se usa su nombre de calle.
        if (calleTelefono != null && !calleTelefono.isBlank() && esLaCalleTipeada(calleTipeada, calleTelefono)) {
            if (pinPrevio != null) confirmarPin(pinPrevio, calleTipeada, numero, lat, lng, "el teléfono confirma la calle");
            else direccionCache.guardar(calleTipeada, numero, DireccionUtils.expandirAbreviaturas(calleTelefono.trim()),
                    limpiarLocalidad(localidadTelefono == null ? "" : localidadTelefono), lat, lng, false, fuente);
            return;
        }
        // Ni el mapa ni el teléfono confirman la calle (Colombia 4695): el cadete igual corrige el pin
        // si marcó cerca, dentro del radio en que la app lo deja marcar "en el lugar".
        if (pinPrevio != null) {
            double distancia = GeocodingService.distanciaKm(pinPrevio.getLat(), pinPrevio.getLng(), lat, lng) * 1000;
            if (distancia <= DISTANCIA_CADETE_CORRIGE_PIN_M) {
                confirmarPin(pinPrevio, calleTipeada, numero, lat, lng, "marcó a " + Math.round(distancia) + " m del pin");
            } else {
                log.info("El GPS del cadete no corrige el pin de \"{}\": marcó a {} m y ni el mapa ni el teléfono "
                        + "confirman la calle.", direccion, Math.round(distancia));
            }
            return;
        }
        // Donde OSM no conoce la calle (2026-09-26, Colombia 4695: calles sin nombre y "Camino del
        // Perú" a 113 m) el reverse nunca confirma y no se aprendía nunca. Se confía igual en el
        // link de Google Maps (el punto es de Google), y en el pin a mano — sin confirmar, con
        // confianza baja, así lo pisa lo que aprenda un cadete. Desde el 2026-09-28 también si el
        // reverse trajo OTRA calle (antes solo si no traía ninguna): el mapa se equivoca justo ahí.
        String fuenteSinConfirmar = DireccionCacheService.PROVEEDOR_GOOGLE_LINK.equals(fuente) ? fuente
                : DireccionCacheService.PROVEEDOR_MANUAL.equals(fuente)
                        || DireccionCacheService.PROVEEDOR_MANUAL_SIN_CONFIRMAR.equals(fuente)
                        ? DireccionCacheService.PROVEEDOR_MANUAL_SIN_CONFIRMAR : null;
        if (fuenteSinConfirmar != null) {
            String localidad = r == null || r.locality() == null ? "" : limpiarLocalidad(r.locality());
            direccionCache.guardar(calleTipeada, numero, DireccionUtils.expandirAbreviaturas(calleTipeada),
                    localidad, lat, lng, false, fuenteSinConfirmar);
            log.info("Pin de \"{}\" aprendido sin confirmar por el mapa ({}; el reverse dio \"{}\").", direccion,
                    fuenteSinConfirmar, r == null ? null : r.street());
            return;
        }
        log.info("No se aprende el pin de \"{}\": el reverse dio \"{}\" y el teléfono \"{}\".", direccion,
                r == null ? null : r.street(), calleTelefono);
    }

    /** El punto del cadete pasa a ser el de la dirección, con la confianza más alta (pisa al pin). */
    private void confirmarPin(CuadraCoords pin, String calleTipeada, int numero, double lat, double lng, String motivo) {
        direccionCache.guardar(calleTipeada, numero, pin.getCalleCanonica(), pin.getLocalidad(), lat, lng, false,
                DireccionCacheService.PROVEEDOR_CADETE_GPS);
        log.info("Pin de \"{} {}\" ({}) confirmado por el GPS del cadete: {}.", calleTipeada, numero, pin.getProveedor(), motivo);
    }

    private static final Set<String> PALABRAS_GENERICAS = Set.of(
            "av", "avda", "avenida", "calle", "pasaje", "pje", "gral", "general", "dr", "doctor",
            "de", "del", "la", "las", "los", "el", "san", "santa", "presidente", "pte");

    /**
     * Antes de guardar lo que devolvió un buscador para lo que tipeó alguien (2026-09-26): si el
     * buscador devolvió OTRA calle ("Presidente Néstor Kirchner" -> "Presidente Perón"), guardarlo
     * creaba un alias permanente equivocado y la próxima búsqueda de Kirchner daba Perón desde la cache.
     */
    private boolean esLaCalleTipeada(String tipeada, String devuelta) {
        return mismaCalle(tipeada, devuelta) || direccionCache.mismaCanonica(tipeada, devuelta);
    }

    /** "av mate de luna" vs "Avenida Mate de Luna": alcanza con compartir una palabra que no sea genérica. */
    static boolean mismaCalle(String tipeada, String reverse) {
        String a = DireccionUtils.normalizar(tipeada), b = DireccionUtils.normalizar(reverse);
        if (a.isEmpty() || b.isEmpty()) return false;
        if (a.equals(b) || a.contains(b) || b.contains(a)) return true;
        Set<String> palabrasB = new HashSet<>(Arrays.asList(b.split(" ")));
        for (String p : a.split(" ")) {
            if (p.length() >= 3 && !PALABRAS_GENERICAS.contains(p) && palabrasB.contains(p)) return true;
        }
        return false;
    }

    /**
     * Calle y altura de un punto (2026-09-26: con respaldo). Primero Nominatim; si no encuentra la
     * calle o no sabe la altura, prueba LocationIQ y después Geoapify (tienen otros datos de
     * alturas), hasta {@code reverse_respaldo_max_dia} consultas por día para no comerse el cupo que
     * usa el buscador de los clientes. Lo que sale con altura alimenta la cache.
     */
    public GeoAddress reverse(double lat, double lng) {
        GeoAddress r = reverseProveedores(lat, lng);
        if (r != null) alimentarCacheSiEsPreciso(r);
        return r;
    }

    /**
     * El reverse que pide el panel o /pedir (cola de espera, pin arrastrado), 2026-09-28:
     * <ul>
     *   <li>primero la base propia: el punto aprendido más cercano (menos de {@value #RADIO_CALLE_PROPIA_M}
     *       m) de un cadete, del teléfono o de un pin (3i/3j: donde OSM tiene calles sin nombre engancha
     *       la avenida de al lado y el panel decía "Camino del Perú" en Colombia 4695);</li>
     *   <li>y NO alimenta la cache: mirar el panel no confirma nada (3i: "camino del peru 1600" llegó a
     *       18 confirmaciones solo de tener la cola de espera abierta). El mapeo de calles de los
     *       cadetes y el aprendizaje de pines siguen usando {@link #reverse}.</li>
     * </ul>
     * Lo que sale de la base propia viene con {@code proveedor = "cache"} y la altura redondeada a la cuadra.
     */
    public GeoAddress reverseParaConsulta(double lat, double lng) {
        CuadraCoords propia = direccionCache.masCercanaPropia(lat, lng, RADIO_CALLE_PROPIA_M);
        if (propia != null) {
            String calle = DireccionUtils.nombreParaMostrar(propia.getCalleCanonica());
            String base = calle + " al " + propia.getCuadra();
            String loc = propia.getLocalidad();
            return new GeoAddress(loc == null || loc.isBlank() ? base : base + ", " + loc, calle, propia.getCuadra(), loc,
                    lat, lng, true, PROVEEDOR_CACHE);
        }
        return reverseProveedores(lat, lng);
    }

    GeoAddress reverseProveedores(double lat, double lng) {
        GeoAddress r = reverseNominatim(lat, lng);
        if ((r == null || r.approximate()) && permitirRespaldo()) {
            GeoAddress respaldo = reverseLocationIq(lat, lng);
            if (respaldo == null || respaldo.approximate()) {
                GeoAddress geoapify = reverseGeoapify(lat, lng);
                if (geoapify != null && (respaldo == null || !geoapify.approximate())) respaldo = geoapify;
            }
            if (respaldo != null && (r == null || !respaldo.approximate())) r = respaldo;
        }
        return r;
    }

    private synchronized boolean permitirRespaldo() {
        int max = configuracionService.getInt(CONFIG_REVERSE_RESPALDO_MAX_DIA, 500);
        if (max <= 0) return false;
        LocalDate hoy = LocalDate.now();
        if (!hoy.equals(diaRespaldo)) {
            diaRespaldo = hoy;
            usosRespaldoHoy = 0;
        }
        if (usosRespaldoHoy >= max) return false;
        usosRespaldoHoy++;
        return true;
    }

    private GeoAddress reverseLocationIq(double lat, double lng) {
        String key = apiKeyPool.siguienteClave(PROVEEDOR_LOCATIONIQ, CONFIG_LOCATIONIQ_KEYS, locationIqKeyLegado);
        if (key == null || key.isBlank()) return null;
        String url = LOCATIONIQ_REVERSE_URL + "?key=" + key + "&lat=" + lat + "&lon=" + lng
                + "&format=json&addressdetails=1&accept-language=es";
        try {
            apiKeyPool.registrarUso(PROVEEDOR_LOCATIONIQ, CONFIG_LOCATIONIQ_KEYS, key);
            Map<String, Object> p = restClient.get().uri(url).header("Accept", "application/json").retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});
            if (p == null || p.containsKey("error")) return null;
            @SuppressWarnings("unchecked")
            Map<String, Object> address = (Map<String, Object>) p.get("address");
            if (address == null || blank(address.get("road"))) return null;
            GeoAddress base = desdeNominatim(p, address, null, PROVEEDOR_LOCATIONIQ);
            // Las coordenadas son las del punto consultado (el GPS), no las de la casa que encontró.
            return new GeoAddress(base.label(), base.street(), base.number(), base.locality(), lat, lng, base.approximate(), base.proveedor());
        } catch (HttpClientErrorException e) {
            apiKeyPool.reportarError(PROVEEDOR_LOCATIONIQ, CONFIG_LOCATIONIQ_KEYS, key, e.getStatusCode().value());
            return null;
        } catch (Exception e) {
            log.debug("Fallo el reverse de LocationIQ de {},{}: {}", lat, lng, e.getMessage());
            return null;
        }
    }

    private GeoAddress reverseGeoapify(double lat, double lng) {
        String key = apiKeyPool.siguienteClave(PROVEEDOR_GEOAPIFY, CONFIG_GEOAPIFY_KEYS, geoapifyKeyLegado);
        if (key == null || key.isBlank()) return null;
        String url = GEOAPIFY_REVERSE_URL + "?apiKey=" + key + "&lat=" + lat + "&lon=" + lng + "&format=json&lang=es";
        try {
            apiKeyPool.registrarUso(PROVEEDOR_GEOAPIFY, CONFIG_GEOAPIFY_KEYS, key);
            Map<String, Object> data = restClient.get().uri(url).retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});
            if (data == null) return null;
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> results = (List<Map<String, Object>>) data.get("results");
            if (results == null || results.isEmpty() || blank(results.get(0).get("street"))) return null;
            GeoAddress base = desdeGeoapify(results.get(0), null);
            return new GeoAddress(base.label(), base.street(), base.number(), base.locality(), lat, lng, base.approximate(), base.proveedor());
        } catch (HttpClientErrorException e) {
            apiKeyPool.reportarError(PROVEEDOR_GEOAPIFY, CONFIG_GEOAPIFY_KEYS, key, e.getStatusCode().value());
            return null;
        } catch (Exception e) {
            log.debug("Fallo el reverse de Geoapify de {},{}: {}", lat, lng, e.getMessage());
            return null;
        }
    }

    private GeoAddress reverseNominatim(double lat, double lng) {
        String url = NOMINATIM_REVERSE_URL + "?format=jsonv2&lat=" + lat + "&lon=" + lng
                + "&addressdetails=1&accept-language=es&zoom=18";
        try {
            Map<String, Object> p = restClient.get().uri(url)
                    .header("Accept", "application/json")
                    .header("User-Agent", NOMINATIM_USER_AGENT)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});
            nominatimOk = true;
            if (p == null || p.containsKey("error")) return null;
            @SuppressWarnings("unchecked")
            Map<String, Object> address = (Map<String, Object>) p.get("address");
            if (address == null || blank(address.get("road"))) return null;
            GeoAddress base = desdeNominatim(p, address, null);
            return new GeoAddress(base.label(), base.street(), base.number(), base.locality(), lat, lng, base.approximate(), base.proveedor());
        } catch (Exception e) {
            nominatimOk = false;
            log.warn("Fallo el reverse geocoding de {},{}: {}", lat, lng, e.getMessage());
            return null;
        }
    }

    /**
     * Un reverse con altura conocida (no aproximado) es un punto CONFIRMADO por GPS real — sirve
     * igual que un geocode normal para alimentar la cache (spec §12, versión simple: sin pedido de
     * por medio no hay variante de cliente que guardar, así que se usa la propia calle canónica
     * como "alias identidad" — quien después escriba el nombre de la calle tal cual pega directo).
     * Lo dispara tanto el reverse del panel ("cadetes libres", pin arrastrado) como
     * {@link MapeoCallesCadetesService}.
     */
    private void alimentarCacheSiEsPreciso(GeoAddress r) {
        if (r.approximate() || r.number() == null) return;
        direccionCache.guardar(r.street(), r.number(), r.street(), r.locality(), r.lat(), r.lng(), false, r.proveedor());
    }

    private List<GeoAddress> queryNominatim(String texto, Integer numeroEsperado) {
        String url = NOMINATIM_URL + "?format=jsonv2&limit=12&countrycodes=ar&addressdetails=1"
                + "&accept-language=es&viewbox=" + TUCUMAN_VIEWBOX
                + "&q=" + encode(texto + ", Tucumán");
        try {
            List<Map<String, Object>> data = restClient.get().uri(url)
                    .header("Accept", "application/json")
                    .header("User-Agent", NOMINATIM_USER_AGENT)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
            nominatimOk = true;
            if (data == null) return List.of();
            List<GeoAddress> out = new ArrayList<>();
            for (Map<String, Object> p : data) {
                @SuppressWarnings("unchecked")
                Map<String, Object> address = (Map<String, Object>) p.get("address");
                if (address == null || !"Tucumán".equals(address.get("state")) || blank(address.get("road"))
                        || p.get("lat") == null || p.get("lon") == null) continue;
                out.add(desdeNominatim(p, address, numeroEsperado));
            }
            return out;
        } catch (Exception e) {
            nominatimOk = false;
            log.warn("Fallo la búsqueda en Nominatim de \"{}\": {}", texto, e.getMessage());
            return List.of();
        }
    }

    private List<GeoAddress> queryGeoapify(String texto, Integer numeroEsperado) {
        for (int intento = 0; intento < MAX_INTENTOS_POR_PROVEEDOR; intento++) {
            String key = apiKeyPool.siguienteClave(PROVEEDOR_GEOAPIFY, CONFIG_GEOAPIFY_KEYS, geoapifyKeyLegado);
            if (key == null || key.isBlank()) return List.of();
            String url = GEOAPIFY_URL + "?apiKey=" + key + "&format=json&limit=12&lang=es"
                    + "&filter=countrycode:ar&bias=rect:" + TUCUMAN_VIEWBOX
                    + "&text=" + encode(texto + ", Tucumán");
            try {
                apiKeyPool.registrarUso(PROVEEDOR_GEOAPIFY, CONFIG_GEOAPIFY_KEYS, key);
                Map<String, Object> data = restClient.get().uri(url)
                        .retrieve()
                        .body(new ParameterizedTypeReference<Map<String, Object>>() {});
                if (data == null) return List.of();
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> results = (List<Map<String, Object>>) data.get("results");
                if (results == null) return List.of();
                List<GeoAddress> out = new ArrayList<>();
                for (Map<String, Object> r : results) {
                    String state = String.valueOf(r.getOrDefault("state", ""));
                    if (!state.toLowerCase().contains("tucum") || blank(r.get("street"))
                            || r.get("lat") == null || r.get("lon") == null) continue;
                    out.add(desdeGeoapify(r, numeroEsperado));
                }
                return out;
            } catch (HttpClientErrorException e) {
                if (apiKeyPool.reportarError(PROVEEDOR_GEOAPIFY, CONFIG_GEOAPIFY_KEYS, key, e.getStatusCode().value())) continue;
                log.warn("Fallo la búsqueda en Geoapify de \"{}\": {}", texto, e.getMessage());
                return List.of();
            } catch (Exception e) {
                log.warn("Fallo la búsqueda en Geoapify de \"{}\": {}", texto, e.getMessage());
                return List.of();
            }
        }
        return List.of();
    }

    private List<GeoAddress> queryLocationIq(String texto, Integer numeroEsperado) {
        for (int intento = 0; intento < MAX_INTENTOS_POR_PROVEEDOR; intento++) {
            String key = apiKeyPool.siguienteClave(PROVEEDOR_LOCATIONIQ, CONFIG_LOCATIONIQ_KEYS, locationIqKeyLegado);
            if (key == null || key.isBlank()) return List.of();
            String url = LOCATIONIQ_URL + "?key=" + key + "&format=json&limit=12&countrycodes=ar&addressdetails=1"
                    + "&accept-language=es&viewbox=" + TUCUMAN_VIEWBOX
                    + "&q=" + encode(texto + ", Tucumán");
            try {
                apiKeyPool.registrarUso(PROVEEDOR_LOCATIONIQ, CONFIG_LOCATIONIQ_KEYS, key);
                List<Map<String, Object>> data = restClient.get().uri(url)
                        .header("Accept", "application/json")
                        .retrieve()
                        .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
                if (data == null) return List.of();
                List<GeoAddress> out = new ArrayList<>();
                for (Map<String, Object> p : data) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> address = (Map<String, Object>) p.get("address");
                    if (address == null || !"Tucumán".equals(address.get("state")) || blank(address.get("road"))
                            || p.get("lat") == null || p.get("lon") == null) continue;
                    out.add(desdeNominatim(p, address, numeroEsperado, PROVEEDOR_LOCATIONIQ));
                }
                return out;
            } catch (HttpClientErrorException e) {
                if (apiKeyPool.reportarError(PROVEEDOR_LOCATIONIQ, CONFIG_LOCATIONIQ_KEYS, key, e.getStatusCode().value())) continue;
                log.warn("Fallo la búsqueda en LocationIQ de \"{}\": {}", texto, e.getMessage());
                return List.of();
            } catch (Exception e) {
                log.warn("Fallo la búsqueda en LocationIQ de \"{}\": {}", texto, e.getMessage());
                return List.of();
            }
        }
        return List.of();
    }

    private GeoAddress desdeNominatim(Map<String, Object> p, Map<String, Object> address, Integer numeroEsperado) {
        return desdeNominatim(p, address, numeroEsperado, PROVEEDOR_NOMINATIM);
    }

    /** LocationIQ replica el formato de respuesta de Nominatim, así que reutiliza el mismo parseo (ver front `geocoding.service.ts`). */
    private GeoAddress desdeNominatim(Map<String, Object> p, Map<String, Object> address, Integer numeroEsperado, String proveedor) {
        String street = String.valueOf(address.getOrDefault("road", ""));
        Integer numero = parseNumero(address.get("house_number"), numeroEsperado);
        String locality = limpiarLocalidad(primero(address, "city", "town", "village", "municipality", "suburb", "neighbourhood", "county"));
        String base = street.isBlank() ? String.valueOf(p.getOrDefault("display_name", "")) : street + (numero != null ? " " + numero : "");
        String label = !street.isBlank() && !locality.isBlank() ? base + ", " + locality : base;
        return new GeoAddress(
                label.isBlank() ? "Dirección" : label, street, numero, locality,
                Double.parseDouble(String.valueOf(p.get("lat"))), Double.parseDouble(String.valueOf(p.get("lon"))),
                address.get("house_number") == null, proveedor);
    }

    private GeoAddress desdeGeoapify(Map<String, Object> r, Integer numeroEsperado) {
        String street = String.valueOf(r.getOrDefault("street", ""));
        Integer numero = parseNumero(r.get("housenumber"), numeroEsperado);
        String locality = limpiarLocalidad(primero(r, "city", "suburb", "county"));
        String base = street.isBlank() ? "" : street + (numero != null ? " " + numero : "");
        String label = !street.isBlank() && !locality.isBlank() ? base + ", " + locality : (base.isBlank() ? "Dirección" : base);
        return new GeoAddress(
                label, street, numero, locality,
                Double.parseDouble(String.valueOf(r.get("lat"))), Double.parseDouble(String.valueOf(r.get("lon"))),
                r.get("housenumber") == null, PROVEEDOR_GEOAPIFY);
    }

    private GeoAddress conNumero(GeoAddress r, int numero) {
        String base = r.street() + " " + numero;
        String label = r.locality() != null && !r.locality().isBlank() ? base + ", " + r.locality() : base;
        return new GeoAddress(label, r.street(), numero, r.locality(), r.lat(), r.lng(), true, r.proveedor());
    }

    private List<GeoAddress> dedupe(List<GeoAddress> lista) {
        List<GeoAddress> ordenada = new ArrayList<>(lista);
        ordenada.sort((a, b) -> Boolean.compare(a.approximate(), b.approximate()));
        Set<String> vistos = new LinkedHashSet<>();
        List<GeoAddress> out = new ArrayList<>();
        for (GeoAddress a : ordenada) {
            String clave = a.street().toLowerCase() + "|" + a.locality().toLowerCase();
            if (vistos.add(clave)) out.add(a);
            if (out.size() >= 7) break;
        }
        return out;
    }

    private Integer parseNumero(Object crudo, Integer porDefecto) {
        if (crudo == null) return porDefecto;
        try {
            return Integer.parseInt(String.valueOf(crudo));
        } catch (NumberFormatException e) {
            return porDefecto;
        }
    }

    private String primero(Map<String, Object> m, String... claves) {
        for (String c : claves) {
            Object v = m.get(c);
            if (v != null && !String.valueOf(v).isBlank()) return String.valueOf(v);
        }
        return "";
    }

    private String limpiarLocalidad(String s) {
        return s.replaceFirst("(?i)^Municipio de\\s+", "").trim();
    }

    private boolean blank(Object o) {
        return o == null || String.valueOf(o).isBlank();
    }

    private String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
