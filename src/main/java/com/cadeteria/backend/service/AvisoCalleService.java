package com.cadeteria.backend.service;

import com.cadeteria.backend.common.TooManyRequestsException;
import com.cadeteria.backend.dto.AvisoCalleDtos.AvisoCalleResponse;
import com.cadeteria.backend.model.AvisoCalle;
import com.cadeteria.backend.model.Cadete;
import com.cadeteria.backend.repository.AvisoCalleRepository;
import com.cadeteria.backend.repository.CadeteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * "Avisos de la calle" (carril C, 2026-09-28): un cadete avisa un control, una calle cortada, un
 * accidente o un piquete; les llega por WebSocket a los cadetes no desconectados que andan a menos de
 * {@code avisos_calle_radio_m} (1000 m) — menos al que avisó — y al Mapa del panel. Vence a los
 * {@code avisos_calle_duracion_min} (60). Contra avisos falsos: solo cadetes logueados, tope de
 * {@value #TOPE_POR_HORA} por hora y cada aviso guarda quién lo hizo. Nadie lo aprueba.
 */
@Service
@Transactional
public class AvisoCalleService {

    private static final Logger log = LoggerFactory.getLogger(AvisoCalleService.class);

    static final int TOPE_POR_HORA = 5;
    static final String CLAVE_DURACION_MIN = "avisos_calle_duracion_min";
    static final String CLAVE_RADIO_M = "avisos_calle_radio_m";
    /** Una posición más vieja que esto no dice dónde anda el cadete ahora. */
    static final Duration POSICION_MAX_ANTIGUEDAD = Duration.ofMinutes(10);

    private final AvisoCalleRepository repo;
    private final CadeteRepository cadeteRepo;
    private final ConfiguracionService configuracion;
    private final GeocodingProxyService geocoding;
    private final WebSocketPublisher publisher;

    public AvisoCalleService(AvisoCalleRepository repo, CadeteRepository cadeteRepo, ConfiguracionService configuracion,
                             GeocodingProxyService geocoding, WebSocketPublisher publisher) {
        this.repo = repo;
        this.cadeteRepo = cadeteRepo;
        this.configuracion = configuracion;
        this.geocoding = geocoding;
        this.publisher = publisher;
    }

    public AvisoCalle crear(String cadeteUsername, String tipo, double lat, double lng) {
        Cadete cadete = cadeteRepo.findByUsername(cadeteUsername)
                .orElseThrow(() -> com.cadeteria.backend.common.ResourceNotFoundException.of("Cadete", cadeteUsername));
        Instant ahora = Instant.now();
        if (repo.countByCadeteIdAndCreadoEnAfter(cadete.getId(), ahora.minus(Duration.ofHours(1))) >= TOPE_POR_HORA) {
            throw new TooManyRequestsException("Ya mandaste " + TOPE_POR_HORA
                    + " avisos en la última hora. Esperá un rato para mandar otro.");
        }
        AvisoCalle aviso = new AvisoCalle();
        aviso.setId(UUID.randomUUID().toString());
        aviso.setTipo(tipo);
        aviso.setLat(lat);
        aviso.setLng(lng);
        aviso.setCalle(calleDe(lat, lng));
        aviso.setCadete(cadete);
        aviso.setCreadoEn(ahora);
        aviso.setVenceEn(ahora.plus(Duration.ofMinutes(configuracion.getInt(CLAVE_DURACION_MIN, 60))));
        repo.save(aviso);

        AvisoCalleResponse paraCadetes = AvisoCalleResponse.paraCadete(aviso);
        for (Cadete destinatario : destinatarios(aviso, cadeteRepo.findAll(), ahora)) {
            publisher.publicarAvisoCalle(destinatario.getId(), paraCadetes);
        }
        publisher.publicarAvisoCalleAdmin(AvisoCalleResponse.paraPanel(aviso));
        return aviso;
    }

    /** Cadetes a los que les llega: no el que avisó, no desconectados ni inactivos, con posición reciente y cerca. */
    List<Cadete> destinatarios(AvisoCalle aviso, List<Cadete> cadetes, Instant ahora) {
        int radio = configuracion.getInt(CLAVE_RADIO_M, 1000);
        return cadetes.stream()
                .filter(c -> !c.getId().equals(aviso.getCadete().getId()))
                .filter(Cadete::isActivo)
                .filter(c -> c.getEstado() != null && !"DESCONECTADO".equals(c.getEstado().getId()))
                .filter(c -> c.getLat() != null && c.getLng() != null && c.getUbicacionActualizadaEn() != null)
                .filter(c -> !c.getUbicacionActualizadaEn().isBefore(ahora.minus(POSICION_MAX_ANTIGUEDAD)))
                .filter(c -> GeocodingService.distanciaKm(c.getLat(), c.getLng(), aviso.getLat(), aviso.getLng()) * 1000 <= radio)
                .toList();
    }

    /** Activos a menos del radio de un punto (la lista "Avisos cerca tuyo" de la app). */
    @Transactional(readOnly = true)
    public List<AvisoCalle> activosCerca(double lat, double lng) {
        int radio = configuracion.getInt(CLAVE_RADIO_M, 1000);
        return repo.findByVenceEnAfterOrderByCreadoEnDesc(Instant.now()).stream()
                .filter(a -> GeocodingService.distanciaKm(lat, lng, a.getLat(), a.getLng()) * 1000 <= radio)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AvisoCalle> activos() {
        return repo.findByVenceEnAfterOrderByCreadoEnDesc(Instant.now());
    }

    /**
     * "Mate de Luna 2400": calle y altura redondeada a la cuadra. null si no se pudo (se muestra
     * "cerca de tu ubicación"). Pendiente al juntar con el carril A: usar
     * {@code geocoding.reverseParaConsulta(lat, lng)} (primero la base propia, no alimenta la cache);
     * ese método todavía no está en esta rama.
     */
    String calleDe(double lat, double lng) {
        try {
            GeocodingProxyService.GeoAddress r = geocoding.reverse(lat, lng);
            if (r == null || r.street() == null || r.street().isBlank()) return null;
            return r.number() == null ? r.street() : r.street() + " " + (r.number() / 100) * 100;
        } catch (RuntimeException e) {
            log.warn("Aviso de la calle sin calle ({}, {}): {}", lat, lng, e.getMessage());
            return null;
        }
    }
}
