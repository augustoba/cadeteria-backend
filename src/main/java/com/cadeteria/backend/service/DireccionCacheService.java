package com.cadeteria.backend.service;

import com.cadeteria.backend.model.CalleNombreAnterior;
import com.cadeteria.backend.model.CuadraCoords;
import com.cadeteria.backend.model.DireccionAlias;
import com.cadeteria.backend.repository.CalleNombreAnteriorRepository;
import com.cadeteria.backend.repository.CuadraCoordsRepository;
import com.cadeteria.backend.repository.DireccionAliasRepository;
import com.cadeteria.backend.util.CallesParecidas;
import com.cadeteria.backend.util.DireccionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Cache de "texto de calle + altura -> coordenadas", compartida por todo lo que geocodifica
 * (ver documentacion/spec-geocoding-cache.md §5-6). Guarda por CUADRA (no por número exacto) y
 * por calle CANÓNICA (no por como la tipeó el cliente), así variantes como "av perón 1500" y
 * "perón 1502" pegan en la misma fila. {@link GeocodingProxyService} es quien la consulta antes
 * de llamar a un proveedor y quien la alimenta después de un geocode real.
 * <p>
 * Fuentes (columna {@code proveedor}): los gratuitos (Nominatim/Geoapify/LocationIQ) y el GPS
 * real de los cadetes quedan para siempre; lo mismo el pin que el admin/cliente ubicó a mano
 * ({@link #PROVEEDOR_MANUAL}). Lo que vino de la API de Google ({@code google}) VENCE: sus
 * condiciones permiten guardar las coordenadas de Geocoding hasta 30 días corridos (2026-09-25),
 * así que se ignora al leer y se borra pasados {@code google_cache_dias} (Configuración, default
 * 30; 0 = no se guarda). El pin sacado de un link de Google Maps ({@link #PROVEEDOR_GOOGLE_LINK})
 * es zona gris: por defecto no vence, pero {@code google_link_vence} lo suma a la misma regla.
 * Si una fuente propia confirma una cuadra que vino de Google, la pisa y pasa a ser propia.
 * <p>
 * {@code google_cache_pausar_borrado} es SOLO para probar en dev (no se borra ni se filtra nada
 * vencido) — antes de producción ver pendientes.md.
 */
@Service
public class DireccionCacheService {

    private static final Logger log = LoggerFactory.getLogger(DireccionCacheService.class);

    public record ResultadoCache(String calleCanonica, String localidad, int cuadra, double lat, double lng, boolean approximate) {}

    /** Pin ubicado a mano (doble clic / arrastrado) por quien carga el pedido. No vence. */
    public static final String PROVEEDOR_MANUAL = "manual";
    /** Pin sacado de un link de Google Maps pegado en el buscador. Vence solo con {@link #CONFIG_GOOGLE_LINK_VENCE}. */
    public static final String PROVEEDOR_GOOGLE_LINK = "google_link";
    /**
     * Pin a mano que el mapa no pudo confirmar (el reverse no trajo calle, 2026-09-26). Se aprende
     * igual pero con confianza de buscador gratuito: lo pisa lo que después aprenda un cadete.
     */
    public static final String PROVEEDOR_MANUAL_SIN_CONFIRMAR = "manual_sin_confirmar";
    /**
     * GPS del cadete al marcar Retirado/Entregado en esa dirección (2026-09-26): la puerta real,
     * medida con buena precisión. No vence.
     */
    public static final String PROVEEDOR_CADETE_GPS = "cadete_gps";
    /**
     * Calle y altura que resolvió el Geocoder del teléfono del cadete (Android, datos de Google) para
     * su posición mientras anda (2026-09-26). Zona gris con las condiciones de Google: **vence igual
     * que lo de la API de Google** ({@code google_cache_dias}) y la pausa de dev también lo cubre.
     */
    public static final String PROVEEDOR_ANDROID_GEOCODER = "android_geocoder";
    private static final String PROVEEDOR_GOOGLE = "google";
    public static final String CONFIG_DIAS_GOOGLE = "google_cache_dias";
    public static final String CONFIG_PAUSAR_BORRADO = "google_cache_pausar_borrado";
    public static final String CONFIG_GOOGLE_LINK_VENCE = "google_link_vence";
    private static final int DIAS_GOOGLE_DEFAULT = 30;

    private final DireccionAliasRepository aliasRepository;
    private final CuadraCoordsRepository coordsRepository;
    private final ConfiguracionService configuracionService;
    private final CalleNombreAnteriorRepository nombreAnteriorRepository;

    @org.springframework.beans.factory.annotation.Autowired
    public DireccionCacheService(DireccionAliasRepository aliasRepository, CuadraCoordsRepository coordsRepository,
                                 ConfiguracionService configuracionService,
                                 CalleNombreAnteriorRepository nombreAnteriorRepository) {
        this.aliasRepository = aliasRepository;
        this.coordsRepository = coordsRepository;
        this.configuracionService = configuracionService;
        this.nombreAnteriorRepository = nombreAnteriorRepository;
    }

    /** Sin la tabla de nombres anteriores (tests de la cache que no la usan). */
    public DireccionCacheService(DireccionAliasRepository aliasRepository, CuadraCoordsRepository coordsRepository,
                                 ConfiguracionService configuracionService) {
        this(aliasRepository, coordsRepository, configuracionService, null);
    }

    /**
     * Null = miss (hay que geocodificar con algún proveedor). No llama a nadie.
     * <p>
     * El texto que tipea el cliente no trae la localidad ("Rivadavia 500" no dice si es San
     * Miguel de Tucumán o Lules), así que si la misma calle+cuadra quedó cacheada para MÁS DE UNA
     * localidad, no hay forma de saber cuál corresponde sin adivinar — se trata como miss para
     * que el geocoder en vivo vuelva a mostrar las opciones (como en una búsqueda sin cache) en
     * vez de devolver en silencio la localidad equivocada.
     */
    @Transactional
    public ResultadoCache buscar(String calleTexto, int numero) {
        String canonica = canonicaDeAlias(calleTexto);
        return canonica == null ? null : buscarPorCanonica(canonica, numero);
    }

    /** Igual que {@link #buscar} pero con la calle canónica ya resuelta (ej. la que salió de {@link #callesQueEmpiezanCon}). */
    @Transactional
    public ResultadoCache buscarPorCanonica(String calleCanonica, int numero) {
        CuadraCoords c = unicaFila(calleCanonica, numero);
        return c == null ? null : confirmarYMapear(c);
    }

    /**
     * Lo que la base propia sabe de esa calle y altura en TODAS las localidades (2026-10-03). Con una
     * sola es lo mismo que {@link #buscar} (suma la confirmación); con varias ("Belgrano 500" está en
     * San Miguel y en Yerba Buena) las devuelve todas para que elija quien carga, en vez de tratarlo
     * como "no la conozco" y salir a preguntar afuera. Vacía = no hay nada.
     */
    @Transactional
    public List<ResultadoCache> buscarOpciones(String calleTexto, int numero) {
        String canonica = canonicaDeAlias(calleTexto);
        return canonica == null ? List.of() : opcionesPorCanonica(canonica, numero);
    }

    /** Igual que {@link #buscarOpciones} con la calle canónica ya resuelta. */
    @Transactional
    public List<ResultadoCache> opcionesPorCanonica(String calleCanonica, int numero) {
        List<CuadraCoords> filas = filasUtiles(calleCanonica, numero);
        if (filas.size() == 1) return List.of(confirmarYMapear(filas.get(0)));
        return filas.stream().map(DireccionCacheService::mapear).toList();
    }

    /** La fila que respondería {@link #buscar}, sin sumarle una confirmación (para ver de qué fuente es). */
    public CuadraCoords filaDe(String calleTexto, int numero) {
        String canonica = canonicaDeAlias(calleTexto);
        return canonica == null ? null : unicaFila(canonica, numero);
    }

    /** Si esa forma de escribir la calle ya tiene alias (o sea, la cache la conoce). */
    public boolean conoceCalle(String calleTexto) {
        return canonicaDeAlias(calleTexto) != null;
    }

    private String canonicaDeAlias(String calleTexto) {
        String norm = DireccionUtils.normalizar(calleTexto);
        return aliasRepository.findByVarianteNorm(norm)
                // "Av. Gral. Roca" -> "avenida general roca" (2026-09-26), igual que canonicalizar().
                .or(() -> aliasRepository.findByVarianteNorm(DireccionUtils.normalizar(DireccionUtils.expandirAbreviaturas(calleTexto))))
                .map(DireccionAlias::getCalleCanonica)
                .orElse(null);
    }

    private CuadraCoords unicaFila(String calleCanonica, int numero) {
        List<CuadraCoords> candidatos = filasUtiles(calleCanonica, numero);
        return candidatos.size() == 1 ? candidatos.get(0) : null;
    }

    private List<CuadraCoords> filasUtiles(String calleCanonica, int numero) {
        return coordsRepository.findByCalleCanonicaAndCuadra(calleCanonica, DireccionUtils.cuadra(numero))
                // Las aproximadas (guardadas antes del 2026-09-24) no sirven como respuesta y
                // hacían parecer ambigua una cuadra con una sola ubicación buena.
                .stream().filter(c -> !c.isApproximate() && !vencida(c)).toList();
    }

    private static ResultadoCache mapear(CuadraCoords c) {
        return new ResultadoCache(c.getCalleCanonica(), c.getLocalidad(), c.getCuadra(), c.getLat(), c.getLng(), c.isApproximate());
    }

    /** Hasta cuántas cuadras de distancia sirve una cuadra conocida para estimar otra de la misma calle. */
    private static final int CUADRAS_MAX_PARA_ESTIMAR = 3;

    /**
     * Calle conocida pero sin esa cuadra (2026-10-03): la estima con las cuadras vecinas de la misma
     * calle y localidad — a mitad de camino si se conocen la de antes y la de después, o la más
     * cercana si no. Siempre {@code approximate}: el buscador la muestra "sin altura exacta" y quien
     * carga corrige el pin (y ahí sí se aprende). Antes esto lo daban solo los buscadores de afuera.
     */
    @Transactional(readOnly = true)
    public List<ResultadoCache> estimar(String calleCanonica, int numero) {
        int cuadra = DireccionUtils.cuadra(numero);
        Map<String, List<CuadraCoords>> porLocalidad = coordsRepository.findByCalleCanonica(calleCanonica).stream()
                .filter(c -> !c.isApproximate() && !vencida(c))
                .collect(Collectors.groupingBy(CuadraCoords::getLocalidad, LinkedHashMap::new, Collectors.toList()));
        List<ResultadoCache> out = new ArrayList<>();
        for (Map.Entry<String, List<CuadraCoords>> e : porLocalidad.entrySet()) {
            CuadraCoords antes = null, despues = null;
            for (CuadraCoords c : e.getValue()) {
                if (c.getCuadra() < cuadra && (antes == null || c.getCuadra() > antes.getCuadra())) antes = c;
                if (c.getCuadra() > cuadra && (despues == null || c.getCuadra() < despues.getCuadra())) despues = c;
            }
            int max = CUADRAS_MAX_PARA_ESTIMAR * 100;
            boolean sirveAntes = antes != null && cuadra - antes.getCuadra() <= max;
            boolean sirveDespues = despues != null && despues.getCuadra() - cuadra <= max;
            if (sirveAntes && sirveDespues) {
                // Proporcional a la altura pedida entre las dos cuadras conocidas.
                double t = (numero - antes.getCuadra() - 50) / (double) (despues.getCuadra() - antes.getCuadra());
                t = Math.max(0, Math.min(1, t));
                out.add(new ResultadoCache(calleCanonica, e.getKey(), cuadra,
                        antes.getLat() + (despues.getLat() - antes.getLat()) * t,
                        antes.getLng() + (despues.getLng() - antes.getLng()) * t, true));
            } else if (sirveAntes || sirveDespues) {
                CuadraCoords c = sirveAntes ? antes : despues;
                out.add(new ResultadoCache(calleCanonica, e.getKey(), cuadra, c.getLat(), c.getLng(), true));
            }
        }
        return out;
    }

    /** Mínimo de letras para completar una calle por el comienzo: con "sa" saldrían todas las "San…". */
    static final int MIN_LETRAS_PREFIJO = 4;
    private static final int MAX_CALLES_PREFIJO = 5;

    /**
     * Calles conocidas (canónicas) que empiezan con lo tipeado (3h, 2026-09-28): "colom" -> "colombia".
     * Vacío si lo tipeado tiene menos de {@link #MIN_LETRAS_PREFIJO} letras. Busca en los alias, así
     * que también encuentra por un nombre alternativo ("lamad" -> "lamadrid").
     */
    public List<String> callesQueEmpiezanCon(String calleTexto) {
        String norm = DireccionUtils.normalizar(calleTexto);
        if (norm.replace(" ", "").length() < MIN_LETRAS_PREFIJO) return List.of();
        Set<String> canonicas = new LinkedHashSet<>();
        for (DireccionAlias a : aliasRepository.findTop50ByVarianteNormStartingWith(norm)) {
            canonicas.add(a.getCalleCanonica());
            if (canonicas.size() >= MAX_CALLES_PREFIJO) break;
        }
        return List.copyOf(canonicas);
    }

    /**
     * Otras calles conocidas (canónicas) que llevan lo tipeado dentro del nombre, como palabras
     * enteras (2026-10-03): el teléfono a veces llama "Batalla de Suipacha" a la calle que todos
     * escriben "Suipacha", cada nombre abrió su fila y buscando "suipacha 750" no se veía la cuadra
     * aprendida con el otro. No incluye la calle a la que ya apunta lo tipeado.
     */
    public List<String> callesQueContienen(String calleTexto) {
        String norm = DireccionUtils.normalizar(calleTexto);
        if (norm.replace(" ", "").length() < MIN_LETRAS_PREFIJO) return List.of();
        String propia = canonicaDeAlias(calleTexto);
        Set<String> canonicas = new LinkedHashSet<>();
        for (DireccionAlias a : aliasRepository.findTop50ByVarianteNormContaining(norm)) {
            if (a.getCalleCanonica().equals(propia)) continue;
            // "peru" no es "perugia": tiene que estar como palabra entera.
            if (!(" " + a.getVarianteNorm() + " ").contains(" " + norm + " ")) continue;
            canonicas.add(a.getCalleCanonica());
            if (canonicas.size() >= MAX_CALLES_PREFIJO) break;
        }
        return List.copyOf(canonicas);
    }

    /** Cada cuánto se vuelve a leer la lista de calles para buscar por parecido (se aprende una calle nueva cada tanto). */
    private static final Duration VIGENCIA_LISTA_DE_CALLES = Duration.ofMinutes(2);
    private static final int MAX_CALLES_PARECIDAS = 5;

    private record CalleConocida(String varianteNorm, String canonica) {}

    private volatile List<CalleConocida> callesConocidas = List.of();
    private volatile Instant callesConocidasHasta = Instant.EPOCH;

    private List<CalleConocida> callesConocidas() {
        if (Instant.now().isAfter(callesConocidasHasta)) {
            callesConocidas = aliasRepository.findAll().stream()
                    .map(a -> new CalleConocida(a.getVarianteNorm(), a.getCalleCanonica())).toList();
            callesConocidasHasta = Instant.now().plus(VIGENCIA_LISTA_DE_CALLES);
        }
        return callesConocidas;
    }

    /**
     * Calles conocidas que se parecen a lo tipeado (2026-10-03): errores de dedo ("belgarno"), de oído
     * ("bolibar", "irigoyen") y palabras sueltas ("mate luna") — ver {@link CallesParecidas}. Devuelve
     * solo las del mejor puntaje: una si el parecido es claro, varias si hay empate (se ofrecen para
     * elegir, no se adivina). Vacía si lo tipeado ya es una calle conocida o no se parece a ninguna.
     */
    public List<String> callesParecidas(String calleTexto) {
        String norm = DireccionUtils.normalizar(DireccionUtils.expandirAbreviaturas(calleTexto));
        if (norm.replace(" ", "").length() < MIN_LETRAS_PREFIJO) return List.of();
        double mejor = Double.MAX_VALUE;
        Set<String> canonicas = new LinkedHashSet<>();
        for (CalleConocida c : callesConocidas()) {
            double p = CallesParecidas.puntaje(norm, c.varianteNorm());
            if (p == CallesParecidas.NO || p > mejor) continue;
            if (p < mejor) {
                mejor = p;
                canonicas.clear();
            }
            canonicas.add(c.canonica());
        }
        return canonicas.stream().limit(MAX_CALLES_PARECIDAS).toList();
    }

    /** Calles que antes se llamaban como lo tipeado ("rivadavia" -> "virgen de la merced"), ver {@link CalleNombreAnterior}. */
    public List<String> callesConNombreAnterior(String calleTexto) {
        if (nombreAnteriorRepository == null) return List.of();
        Set<String> canonicas = new LinkedHashSet<>();
        // Tal cual y con las abreviaturas expandidas ("gral roca" / "general roca"); casi siempre es el mismo texto.
        Set<String> nombres = new LinkedHashSet<>(List.of(DireccionUtils.normalizar(calleTexto)));
        nombres.add(DireccionUtils.normalizar(DireccionUtils.expandirAbreviaturas(calleTexto)));
        for (String nombre : nombres) {
            for (CalleNombreAnterior n : nombreAnteriorRepository.findByNombreNorm(nombre)) {
                // El Excel se carga con el nombre de hoy; si ese nombre es alias de otro, vale la canónica.
                canonicas.add(canonicalizar(n.getCalleCanonica()));
            }
        }
        return List.copyOf(canonicas);
    }

    /** Lo que respondería {@link #buscarPorCanonica}, sin sumarle una confirmación: ofrecerla no la confirma. */
    public ResultadoCache mirarPorCanonica(String calleCanonica, int numero) {
        CuadraCoords c = unicaFila(calleCanonica, numero);
        return c == null ? null : mapear(c);
    }

    /** Igual, en todas las localidades donde esa calle tenga la cuadra aprendida. */
    public List<ResultadoCache> mirarOpciones(String calleCanonica, int numero) {
        return filasUtiles(calleCanonica, numero).stream().map(DireccionCacheService::mapear).toList();
    }

    /**
     * El punto aprendido de una fuente propia más cercano a (lat, lng), a menos de {@code radioM}
     * (3i/3j, 2026-09-28): qué calle hay en un punto según lo que ya confirmaron los cadetes o las
     * personas, antes de preguntarle a OpenStreetMap (que en barrios con calles sin nombre engancha
     * la avenida de al lado). Prefiere lo medido en la calle (GPS del cadete, teléfono) a los pines.
     */
    public CuadraCoords masCercanaPropia(double lat, double lng, double radioM) {
        double dLat = radioM / 111_320d;
        double dLng = radioM / (111_320d * Math.cos(Math.toRadians(lat)));
        return coordsRepository.findByLatBetweenAndLngBetween(lat - dLat, lat + dLat, lng - dLng, lng + dLng).stream()
                .filter(c -> FUENTES_PROPIAS.contains(c.getProveedor()) && !c.isApproximate() && !vencida(c))
                .filter(c -> distanciaM(lat, lng, c.getLat(), c.getLng()) <= radioM)
                .min(Comparator.comparingInt((CuadraCoords c) -> MEDIDAS_EN_LA_CALLE.contains(c.getProveedor()) ? 0 : 1)
                        .thenComparingDouble(c -> distanciaM(lat, lng, c.getLat(), c.getLng())))
                .orElse(null);
    }

    /** Pines que puso una persona (panel, /pedir o link de Google Maps): los confirma o corrige el GPS del cadete. */
    public static boolean esPin(String proveedor) {
        return PROVEEDOR_MANUAL.equals(proveedor) || PROVEEDOR_MANUAL_SIN_CONFIRMAR.equals(proveedor)
                || PROVEEDOR_GOOGLE_LINK.equals(proveedor);
    }

    private static final Set<String> MEDIDAS_EN_LA_CALLE = Set.of(PROVEEDOR_CADETE_GPS, PROVEEDOR_ANDROID_GEOCODER);
    /** Los buscadores gratuitos no cuentan: son el mismo dato que devolvería el reverse. */
    private static final Set<String> FUENTES_PROPIAS = Set.of(PROVEEDOR_CADETE_GPS, PROVEEDOR_ANDROID_GEOCODER,
            PROVEEDOR_MANUAL, PROVEEDOR_MANUAL_SIN_CONFIRMAR, PROVEEDOR_GOOGLE_LINK);

    private static double distanciaM(double lat1, double lng1, double lat2, double lng2) {
        return GeocodingService.distanciaKm(lat1, lng1, lat2, lng2) * 1000;
    }

    private ResultadoCache confirmarYMapear(CuadraCoords c) {
        c.setConfirmaciones(c.getConfirmaciones() + 1);
        coordsRepository.save(c);
        return new ResultadoCache(c.getCalleCanonica(), c.getLocalidad(), c.getCuadra(), c.getLat(), c.getLng(), c.isApproximate());
    }

    /**
     * Guarda el resultado de un geocode real para no volver a pagarlo. {@code calleCanonica} debe
     * ser la que devolvió el proveedor (Nominatim: {@code road}; Geoapify: {@code street}), nunca
     * una adivinada localmente (spec §5.4, "alias solo de resultados reales").
     */
    @Transactional
    public void guardar(String calleTextoOriginal, int numero, String calleCanonica, String localidad,
                         double lat, double lng, boolean approximate, String proveedor) {
        if (calleCanonica == null || calleCanonica.isBlank()) return;
        // Solo ubicaciones con la altura exacta: una aproximada se devolvía después como única
        // opción (con la localidad que tocara primero) y el cliente no podía elegir otra.
        if (approximate) return;
        // Días en 0 = no guardar nada de Google (y lo que ya hubiera se ignora al leer).
        if (vence(proveedor) && diasGoogle() <= 0) return;
        // El nombre del proveedor también pasa por los alias (2026-09-26): Nominatim dice "General
        // Lamadrid" y el teléfono "Lamadrid"; sin esto cada uno abría su propia fila y buscar con un
        // nombre no veía lo aprendido con el otro, aunque los alias estuvieran curados.
        String canonicaNorm = canonicalizar(calleCanonica);

        String varianteNorm = DireccionUtils.normalizar(calleTextoOriginal);
        if (aliasRepository.findByVarianteNorm(varianteNorm).isEmpty()) {
            DireccionAlias alias = new DireccionAlias();
            alias.setId(UUID.randomUUID().toString());
            alias.setVarianteNorm(varianteNorm);
            alias.setLocalidad(localidad == null ? "" : localidad);
            alias.setCalleCanonica(canonicaNorm);
            aliasRepository.save(alias);
        }

        int cuadra = DireccionUtils.cuadra(numero);
        String localidadNorm = localidad == null ? "" : localidad;
        coordsRepository.findByCalleCanonicaAndLocalidadAndCuadra(canonicaNorm, localidadNorm, cuadra).ifPresentOrElse(
                existente -> {
                    existente.setConfirmaciones(existente.getConfirmaciones() + 1);
                    String previo = existente.getProveedor();
                    if (java.util.Objects.equals(previo, proveedor) && confianza(proveedor) <= 1) {
                        // Otra pasada de la misma fuente "en movimiento" (buscador o teléfono): se
                        // promedia en vez de quedarse con el primer punto, que suele ser una esquina
                        // (2026-09-26: perú 3700 y paraguay 3800 quedaron en el mismo punto).
                        promediar(existente, lat, lng);
                        if (vence(previo)) existente.setCreadaEn(Instant.now());
                    } else if (vence(previo) || confianza(proveedor) > confianza(previo)
                            // Otro pin sin confirmar en la misma cuadra: el último es el corregido.
                            || (PROVEEDOR_MANUAL_SIN_CONFIRMAR.equals(proveedor) && proveedor.equals(previo))) {
                        // Lo de Google se pisa: con una fuente propia deja de vencer, y con otra
                        // consulta a Google es un dato recién obtenido (vuelve a contar desde hoy).
                        // Y una fuente más confiable pisa a una menos confiable (2026-09-26): el GPS
                        // del cadete en la puerta o un pin puesto a mano corrigen lo que había
                        // interpolado un buscador gratuito.
                        existente.setLat(lat);
                        existente.setLng(lng);
                        existente.setMuestras(1);
                        existente.setApproximate(false);
                        existente.setProveedor(proveedor == null ? "" : proveedor);
                        existente.setCreadaEn(Instant.now());
                    } else if (confianza(proveedor) == confianza(previo) && confianza(proveedor) <= 1 && !vence(proveedor)) {
                        // Dos buscadores gratuitos distintos (nominatim + geoapify): también se promedian.
                        // Lo del teléfono (vence, datos de Google) no se mezcla en una fila que no vence.
                        promediar(existente, lat, lng);
                    }
                    coordsRepository.save(existente);
                },
                () -> {
                    CuadraCoords c = new CuadraCoords();
                    c.setId(UUID.randomUUID().toString());
                    c.setCalleCanonica(canonicaNorm);
                    c.setLocalidad(localidadNorm);
                    c.setCuadra(cuadra);
                    c.setLat(lat);
                    c.setLng(lng);
                    c.setApproximate(approximate);
                    c.setProveedor(proveedor == null ? "" : proveedor);
                    c.setConfirmaciones(1);
                    c.setMuestras(1);
                    coordsRepository.save(c);
                }
        );
    }

    /** Tope del peso del promedio: una cuadra muy recorrida se sigue pudiendo corregir un poco. */
    private static final int MUESTRAS_MAX_PESO = 20;

    private static void promediar(CuadraCoords c, double lat, double lng) {
        int peso = Math.min(c.getMuestras(), MUESTRAS_MAX_PESO);
        c.setLat((c.getLat() * peso + lat) / (peso + 1));
        c.setLng((c.getLng() * peso + lng) / (peso + 1));
        c.setMuestras(c.getMuestras() + 1);
    }

    /**
     * Nombre canónico de una calle: normalizado y, si esa variante tiene alias, el nombre elegido
     * para ella ("general lamadrid" -> "lamadrid", lista curada en pendientes 3k). Sin alias, la
     * misma variante normalizada.
     */
    public String canonicalizar(String calle) {
        String norm = DireccionUtils.normalizar(calle);
        return aliasRepository.findByVarianteNorm(norm).map(DireccionAlias::getCalleCanonica).orElseGet(() -> {
            // "Av. Gral. Roca" -> "avenida general roca", por si el alias está con el nombre completo.
            String expandida = DireccionUtils.normalizar(DireccionUtils.expandirAbreviaturas(calle));
            return expandida.equals(norm) ? norm
                    : aliasRepository.findByVarianteNorm(expandida).map(DireccionAlias::getCalleCanonica).orElse(norm);
        });
    }

    /** Si dos nombres son la misma calle según los alias ("avenida general roca" = "avenida nestor kirchner"). */
    public boolean mismaCanonica(String a, String b) {
        String ca = canonicalizar(a);
        return !ca.isEmpty() && ca.equals(canonicalizar(b));
    }

    /**
     * Qué fuente pisa a cuál en una misma cuadra: GPS del cadete en la puerta &gt; pin a mano
     * confirmado por el mapa y link de Google Maps &gt; pin a mano sin confirmar &gt; buscadores
     * gratuitos &gt; Google API (que además vence). A igual confianza gana la primera y solo se suma
     * una confirmación. 2026-09-28 (3j): el cadete pasó arriba del pin — antes un pin arrastrado por
     * error a 40 m pisaba el punto que el cadete había confirmado parado en la puerta.
     */
    static int confianza(String proveedor) {
        if (proveedor == null) return 1;
        return switch (proveedor) {
            case PROVEEDOR_CADETE_GPS -> 4;
            case PROVEEDOR_MANUAL, PROVEEDOR_GOOGLE_LINK -> 3;
            // Una persona lo vio en el mapa: mejor que lo que interpola un buscador, pero no se
            // promedia con él (ver guardar) y lo pisa un cadete o un pin confirmado.
            case PROVEEDOR_MANUAL_SIN_CONFIRMAR -> 2;
            case GeocodingProxyService.PROVEEDOR_GOOGLE -> 0;
            // Mismo nivel que un buscador gratuito: viene de un punto en movimiento.
            case PROVEEDOR_ANDROID_GEOCODER -> 1;
            default -> 1; // nominatim, geoapify, locationiq
        };
    }

    /**
     * Borra las ubicaciones de Google vencidas, todos los días a las 4:30. El filtro de
     * {@link #buscar} ya las ignora aunque esto no haya corrido todavía.
     */
    @Scheduled(cron = "0 30 4 * * *", zone = "America/Argentina/Buenos_Aires")
    @Transactional
    public long borrarVencidas() {
        if (borradoPausado()) {
            log.warn("Borrado de ubicaciones de Google PAUSADO ({}) — solo para pruebas.", CONFIG_PAUSAR_BORRADO);
            return 0;
        }
        Instant limite = Instant.now().minus(Duration.ofDays(Math.max(0, diasGoogle())));
        long borradas = coordsRepository.deleteByProveedorInAndCreadaEnBefore(proveedoresQueVencen(), limite);
        if (borradas > 0) log.info("Se borraron {} ubicaciones de Google vencidas.", borradas);
        return borradas;
    }

    private boolean vencida(CuadraCoords c) {
        if (!vence(c.getProveedor()) || borradoPausado()) return false;
        return c.getCreadaEn().isBefore(Instant.now().minus(Duration.ofDays(Math.max(0, diasGoogle()))));
    }

    private boolean vence(String proveedor) {
        return proveedor != null && proveedoresQueVencen().contains(proveedor);
    }

    private Set<String> proveedoresQueVencen() {
        Set<String> out = new HashSet<>();
        out.add(PROVEEDOR_GOOGLE);
        out.add(PROVEEDOR_ANDROID_GEOCODER);
        if (configuracionService.getBoolean(CONFIG_GOOGLE_LINK_VENCE, false)) out.add(PROVEEDOR_GOOGLE_LINK);
        return out;
    }

    private int diasGoogle() {
        return configuracionService.getInt(CONFIG_DIAS_GOOGLE, DIAS_GOOGLE_DEFAULT);
    }

    private boolean borradoPausado() {
        return configuracionService.getBoolean(CONFIG_PAUSAR_BORRADO, false);
    }
}
