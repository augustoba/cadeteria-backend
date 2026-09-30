package com.cadeteria.backend.service;

import com.cadeteria.backend.common.TooManyRequestsException;
import com.cadeteria.backend.dto.AvisoCalleDtos.AvisoCalleResponse;
import com.cadeteria.backend.model.AvisoCalle;
import com.cadeteria.backend.model.AvisoCalleVoto;
import com.cadeteria.backend.model.Cadete;
import com.cadeteria.backend.repository.AvisoCalleRepository;
import com.cadeteria.backend.repository.AvisoCalleVotoRepository;
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

    /** "Sigue" lo deja vivo al menos esto más desde ahora (segunda etapa, 2026-09-29). */
    static final String CLAVE_EXTENSION_MIN = "avisos_calle_extension_min";
    /** Con tantos "ya no está" de cadetes distintos se baja. */
    static final String CLAVE_YA_NO_ESTA_PARA_BAJAR = "avisos_calle_ya_no_esta_para_bajar";

    private static final java.time.ZoneId ZONA = java.time.ZoneId.of("America/Argentina/Buenos_Aires");

    /**
     * Ningún aviso pasa de la medianoche (pedido del usuario, 2026-09-29): al terminar el día ya no hay
     * cadetes trabajando por ahí y al otro día no sirve. Vale al crearlo y cuando "Sigue" lo extiende.
     */
    static Instant hastaFinDelDia(Instant vence, Instant ahora) {
        Instant medianoche = ahora.atZone(ZONA).toLocalDate().plusDays(1).atStartOfDay(ZONA).toInstant();
        return vence.isAfter(medianoche) ? medianoche : vence;
    }

    private final AvisoCalleRepository repo;
    private final AvisoCalleVotoRepository votoRepo;
    private final CadeteRepository cadeteRepo;
    private final ConfiguracionService configuracion;
    private final GeocodingProxyService geocoding;
    private final WebSocketPublisher publisher;

    public AvisoCalleService(AvisoCalleRepository repo, AvisoCalleVotoRepository votoRepo, CadeteRepository cadeteRepo,
                             ConfiguracionService configuracion, GeocodingProxyService geocoding, WebSocketPublisher publisher) {
        this.repo = repo;
        this.votoRepo = votoRepo;
        this.cadeteRepo = cadeteRepo;
        this.configuracion = configuracion;
        this.geocoding = geocoding;
        this.publisher = publisher;
    }

    /**
     * "¿Sigue ahí?" (segunda etapa, 2026-09-29): un cadete que pasa cerca contesta. SIGUE → vence en
     * {@code avisos_calle_extension_min} (30) desde ahora si eso es más tarde que lo que tenía.
     * YA_NO_ESTA → con {@code avisos_calle_ya_no_esta_para_bajar} (2) cadetes distintos se baja (vence
     * ya). Un voto por cadete y aviso (si cambia de opinión se actualiza); el que lo avisó no vota.
     * El cambio les llega a los cadetes cercanos y al Mapa del panel.
     */
    public AvisoCalle votar(String cadeteUsername, String avisoId, String voto) {
        Cadete cadete = cadeteRepo.findByUsername(cadeteUsername)
                .orElseThrow(() -> com.cadeteria.backend.common.ResourceNotFoundException.of("Cadete", cadeteUsername));
        AvisoCalle aviso = repo.findById(avisoId)
                .orElseThrow(() -> com.cadeteria.backend.common.ResourceNotFoundException.of("Aviso de la calle", avisoId));
        Instant ahora = Instant.now();
        if (!aviso.getVenceEn().isAfter(ahora)) {
            throw new com.cadeteria.backend.common.ConflictException("Ese aviso ya no está activo.");
        }
        if (aviso.getCadete().getId().equals(cadete.getId())) {
            throw new com.cadeteria.backend.common.BadRequestException("No podés contestar tu propio aviso.");
        }
        AvisoCalleVoto v = votoRepo.findByAvisoIdAndCadeteId(avisoId, cadete.getId()).orElseGet(() -> {
            AvisoCalleVoto nuevo = new AvisoCalleVoto();
            nuevo.setId(UUID.randomUUID().toString());
            nuevo.setAviso(aviso);
            nuevo.setCadete(cadete);
            return nuevo;
        });
        v.setVoto(voto);
        v.setCreadoEn(ahora);
        votoRepo.save(v);

        if (AvisoCalleVoto.SIGUE.equals(voto)) {
            Instant extendido = hastaFinDelDia(ahora.plus(Duration.ofMinutes(configuracion.getInt(CLAVE_EXTENSION_MIN, 30))), ahora);
            if (extendido.isAfter(aviso.getVenceEn())) aviso.setVenceEn(extendido);
        } else if (votoRepo.countByAvisoIdAndVoto(avisoId, AvisoCalleVoto.YA_NO_ESTA)
                >= configuracion.getInt(CLAVE_YA_NO_ESTA_PARA_BAJAR, 2)) {
            aviso.setVenceEn(ahora);
            aviso.setBajadoEn(ahora);
        }
        repo.save(aviso);

        AvisoCalleResponse paraCadetes = AvisoCalleResponse.paraCadete(aviso);
        for (Cadete destinatario : destinatarios(aviso, cadeteRepo.findAll(), ahora)) {
            publisher.publicarAvisoCalle(destinatario.getId(), paraCadetes);
        }
        publisher.publicarAvisoCalleAdmin(AvisoCalleResponse.paraPanel(aviso));
        return aviso;
    }

    @Transactional(readOnly = true)
    public com.cadeteria.backend.dto.AvisoCalleDtos.ResumenAvisosCadete resumenCadete(String cadeteId) {
        return new com.cadeteria.backend.dto.AvisoCalleDtos.ResumenAvisosCadete(
                repo.countByCadeteId(cadeteId),
                votoRepo.avisosDelCadeteMarcadosYaNoEsta(cadeteId),
                repo.countByCadeteIdAndBajadoEnIsNotNull(cadeteId));
    }

    public AvisoCalle crear(String cadeteUsername, String tipo, double lat, double lng) {
        return crear(cadeteUsername, tipo, lat, lng, null, null);
    }

    /** Con la calle del Geocoder del teléfono, si la mandó: tiene prioridad sobre el mapa (ver {@link #calleDe}). */
    public AvisoCalle crear(String cadeteUsername, String tipo, double lat, double lng, String calleTelefono,
                            Integer alturaTelefono) {
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
        aviso.setCalle(calleDe(lat, lng, calleTelefono, alturaTelefono));
        aviso.setCadete(cadete);
        aviso.setCreadoEn(ahora);
        aviso.setVenceEn(hastaFinDelDia(ahora.plus(Duration.ofMinutes(configuracion.getInt(CLAVE_DURACION_MIN, 60))), ahora));
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
     * "cerca de tu ubicación"). Usa {@code reverseParaConsulta} (carril A): primero la base propia y no
     * alimenta la cache de direcciones.
     */
    String calleDe(double lat, double lng, String calleTelefono, Integer alturaTelefono) {
        // Primero la del teléfono (2026-09-29): es la misma que muestra el panel para el cadete. El mapa
        // (OpenStreetMap) se equivoca justo en algunas zonas: en Colombia 4695 decía "Camino del Perú 1600".
        if (calleTelefono != null && !calleTelefono.isBlank()) {
            String calle = calleTelefono.trim();
            return alturaTelefono == null || alturaTelefono <= 0 ? calle : calle + " " + (alturaTelefono / 100) * 100;
        }
        try {
            GeocodingProxyService.GeoAddress r = geocoding.reverseParaConsulta(lat, lng);
            if (r == null || r.street() == null || r.street().isBlank()) return null;
            return r.number() == null ? r.street() : r.street() + " " + (r.number() / 100) * 100;
        } catch (RuntimeException e) {
            log.warn("Aviso de la calle sin calle ({}, {}): {}", lat, lng, e.getMessage());
            return null;
        }
    }
}
