package com.cadeteria.backend.service;

import com.cadeteria.backend.model.CuadraCoords;
import com.cadeteria.backend.model.DireccionAlias;
import com.cadeteria.backend.repository.CuadraCoordsRepository;
import com.cadeteria.backend.repository.DireccionAliasRepository;
import com.cadeteria.backend.util.DireccionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

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

    public DireccionCacheService(DireccionAliasRepository aliasRepository, CuadraCoordsRepository coordsRepository,
                                 ConfiguracionService configuracionService) {
        this.aliasRepository = aliasRepository;
        this.coordsRepository = coordsRepository;
        this.configuracionService = configuracionService;
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
        String norm = DireccionUtils.normalizar(calleTexto);
        return aliasRepository.findByVarianteNorm(norm)
                // "Av. Gral. Roca" -> "avenida general roca" (2026-09-26), igual que canonicalizar().
                .or(() -> aliasRepository.findByVarianteNorm(DireccionUtils.normalizar(DireccionUtils.expandirAbreviaturas(calleTexto))))
                .map(alias -> coordsRepository.findByCalleCanonicaAndCuadra(alias.getCalleCanonica(), DireccionUtils.cuadra(numero))
                        // Las aproximadas (guardadas antes del 2026-09-24) no sirven como respuesta y
                        // hacían parecer ambigua una cuadra con una sola ubicación buena.
                        .stream().filter(c -> !c.isApproximate() && !vencida(c)).toList())
                .filter(candidatos -> candidatos.size() == 1)
                .map(candidatos -> confirmarYMapear(candidatos.get(0)))
                .orElse(null);
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
                    } else if (vence(previo) || confianza(proveedor) > confianza(previo)) {
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
     * Qué fuente pisa a cuál en una misma cuadra (2026-09-26): pin puesto a mano por una persona
     * &gt; GPS del cadete en la puerta y link de Google Maps &gt; buscadores gratuitos &gt; Google API
     * (que además vence). A igual confianza gana la primera y solo se suma una confirmación.
     */
    static int confianza(String proveedor) {
        if (proveedor == null) return 1;
        return switch (proveedor) {
            case PROVEEDOR_MANUAL -> 4;
            case PROVEEDOR_CADETE_GPS, PROVEEDOR_GOOGLE_LINK -> 3;
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
