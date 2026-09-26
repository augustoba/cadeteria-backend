package com.cadeteria.backend.service;

import com.cadeteria.backend.model.Cadete;
import com.cadeteria.backend.repository.CadeteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Enriquecimiento pasivo y gratis de la cache de direcciones (spec-geocoding-cache.md §12,
 * versión simple sin "stop-points"): cada tanto reverse-geocodea la ÚLTIMA posición conocida de
 * los cadetes activos — ya guardada por {@link CadeteService#actualizarUbicacion}, no le pide
 * nada nuevo a la app — y deja que {@link GeocodingProxyService#reverse} alimente la cache si el
 * punto cae sobre una altura conocida.
 * <p>
 * Decisión 2026-09-21: reaccionar a CADA ping de ubicación (cada ~45 seg,
 * `frecuencia_ubicacion_seg`) se descartó — con 20 cadetes ya son ~0.44 llamadas/seg sostenidas a
 * Nominatim SOLO de esto (a los 70 cadetes del plan de expansión, ~1.5/seg), pisando el límite de
 * cortesía de ~1 req/seg de Nominatim, compartido con las búsquedas reales de clientes. Muestrear
 * cada {@code mapeo_calles_cadetes_intervalo_seg} segundos desacopla el volumen de la frecuencia
 * de ping, y además cubre a TODA la flota activa en cada corrida (no solo al que pingueó justo en
 * ese momento).
 * <p>
 * <b>La cuenta de seguridad real es cadetes activos / intervalo (en segundos).</b> El default de
 * producción (1200 seg = 20 min) asume una flota de decenas de cadetes reales. Para "sembrar" la
 * cache antes de tener cadetes reales (probando con 1-5 cuentas propias/de amigos logueadas y
 * moviéndose), un intervalo bien más corto (ej. 30 seg) es seguro porque hay poquísimos cadetes
 * activos a la vez — pero **hay que volver a subirlo** antes de que haya muchos cadetes reales
 * conectados, si no la cuenta deja de dar segura. Por eso el piso {@link #INTERVALO_MINIMO_SEG} no
 * es configurable: aunque alguien tipee "1" en el panel, nunca corre más seguido que eso.
 */
@Service
public class MapeoCallesCadetesService {

    private static final Logger log = LoggerFactory.getLogger(MapeoCallesCadetesService.class);
    /** 0 = apagado — el dueño puede desactivar esto sin tocar código si en algún momento no lo quiere más. */
    public static final String CONFIG_INTERVALO_SEG = "mapeo_calles_cadetes_intervalo_seg";
    /** Piso duro, NO configurable desde el panel — protege a Nominatim aunque se tipee un número más bajo. */
    private static final long INTERVALO_MINIMO_SEG = 10;
    /** Cada cuánto se FIJA si toca correr — tiene que ser más chico que el intervalo mínimo de arriba para poder respetarlo. */
    private static final long TICK_MS = 5_000;
    /** Cadetes sin ping hace más de esto se consideran desconectados — no tiene sentido remapear una posición vieja una y otra vez. */
    private static final Duration UBICACION_MAX_ANTIGUEDAD = Duration.ofMinutes(10);
    /** Pausa entre cada reverse geocode dentro de una corrida — etiqueta de uso de Nominatim (~1 req/seg). */
    private static final long PAUSA_ENTRE_LLAMADAS_MS = 1100;

    private final CadeteRepository cadeteRepository;
    private final GeocodingProxyService geocodingProxyService;
    private final ConfiguracionService configuracionService;

    /**
     * El teléfono resuelve la calle cada ~120 m o 2 min (APK, ThrottleCalle). Si lo hizo hace menos de
     * esto y el cadete no se alejó más de {@link #DISTANCIA_TELEFONO_M}, no se consulta a Nominatim.
     * Antes la ventana era el intervalo del mapeo: con 10 s en la prueba del 2026-09-26 el respaldo
     * corrió casi siempre en paralelo al teléfono (62 % de lo aprendido) e infló una cuadra estando quieto.
     */
    private static final Duration VENTANA_TELEFONO = Duration.ofMinutes(3);
    private static final double DISTANCIA_TELEFONO_M = 120;

    private record CalleResuelta(Instant en, double lat, double lng) {}

    private volatile Instant ultimaCorrida = Instant.EPOCH;
    /** Última calle que resolvió (y se guardó) el teléfono de cada cadete, con dónde estaba (2026-09-26). */
    private final java.util.Map<String, CalleResuelta> calleDelTelefono = new java.util.concurrent.ConcurrentHashMap<>();

    public MapeoCallesCadetesService(CadeteRepository cadeteRepository, GeocodingProxyService geocodingProxyService,
                                      ConfiguracionService configuracionService) {
        this.cadeteRepository = cadeteRepository;
        this.geocodingProxyService = geocodingProxyService;
        this.configuracionService = configuracionService;
    }

    @Scheduled(fixedDelay = TICK_MS)
    public void mapearSiCorresponde() {
        int intervaloSeg = configuracionService.getInt(CONFIG_INTERVALO_SEG, 1200);
        if (intervaloSeg <= 0) return;
        long intervaloEfectivoSeg = Math.max(intervaloSeg, INTERVALO_MINIMO_SEG);
        if (Duration.between(ultimaCorrida, Instant.now()).getSeconds() < intervaloEfectivoSeg) return;
        ultimaCorrida = Instant.now();

        Instant limiteTelefono = Instant.now().minus(VENTANA_TELEFONO);
        for (Cadete c : cadetesActivosConUbicacionReciente()) {
            // Si su teléfono ya resolvió la calle hace poco y cerca de acá, no se gasta otra consulta.
            CalleResuelta resuelta = calleDelTelefono.get(c.getId());
            if (resuelta != null && resuelta.en().isAfter(limiteTelefono)
                    && distanciaM(resuelta.lat(), resuelta.lng(), c.getLat(), c.getLng()) <= DISTANCIA_TELEFONO_M) continue;
            try {
                geocodingProxyService.reverse(c.getLat(), c.getLng());
            } catch (Exception e) {
                log.debug("No se pudo mapear la ubicacion del cadete {}: {}", c.getId(), e.getMessage());
            }
            pausar();
        }
    }

    /** El teléfono del cadete resolvió la calle de esta posición y se guardó (Geocoder de Android). */
    public void registrarCalleDelTelefono(String cadeteId, double lat, double lng) {
        calleDelTelefono.put(cadeteId, new CalleResuelta(Instant.now(), lat, lng));
    }

    static double distanciaM(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1), dLng = Math.toRadians(lng2 - lng1);
        double a = Math.pow(Math.sin(dLat / 2), 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.pow(Math.sin(dLng / 2), 2);
        return 2 * 6_371_000 * Math.asin(Math.sqrt(a));
    }

    private List<Cadete> cadetesActivosConUbicacionReciente() {
        Instant limite = Instant.now().minus(UBICACION_MAX_ANTIGUEDAD);
        return cadeteRepository.findAll().stream()
                .filter(Cadete::isActivo)
                .filter(c -> c.getLat() != null && c.getLng() != null)
                .filter(c -> c.getUbicacionActualizadaEn() != null && c.getUbicacionActualizadaEn().isAfter(limite))
                .toList();
    }

    private void pausar() {
        try {
            Thread.sleep(PAUSA_ENTRE_LLAMADAS_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
