package com.cadeteria.backend.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LinkGoogleMapsServiceTest {

    private final LinkGoogleMapsService service = new LinkGoogleMapsService();

    @Test
    void streetViewUsaLaPosicionDeLaCamara() {
        // Link real de Street View frente a Colombia 4695 (2026-09-25).
        var r = service.resolver("https://www.google.com/maps/@-26.7954738,-65.2568815,486a,90y,24.35h,92.79t/data=!3m7!1e1!3m5!1sFF-0wTyY4zAEOavHK1gwsA!2e0!6shttps:%2F%2Fstreetviewpixels-pa.googleapis.com%2Fv1%2Fthumbnail%3Fcb_client%3Dmaps_sv.tactile%26w%3D900%26h%3D600%26pitch%3D-2.785440487476265%26panoid%3DFF-0wTyY4zAEOavHK1gwsA%26yaw%3D24.34915929103761!7i16384!8i8192?entry=ttu&g_ep=EgoyMDI2MDkyMy4wIKXMDSoASAFQAw%3D%3D");
        assertNull(r.error());
        assertEquals(-26.7954738, r.lat());
        assertEquals(-65.2568815, r.lng());
    }

    @Test
    void unLugarMarcadoPrefiereSusCoordenadasAlCentroDeLaPantalla() {
        var r = service.resolver("https://www.google.com/maps/place/Colombia+4695/@-26.80,-65.26,17z/data=!3m1!4b1!4m6!3m5!1s0x0:0x0!8m2!3d-26.7955!4d-65.2569!16s");
        assertEquals(-26.7955, r.lat());
        assertEquals(-65.2569, r.lng());
    }

    @Test
    void aceptaCoordenadasEnElParametroQ() {
        var r = service.resolver("https://maps.google.com/?q=-26.8083,-65.2176");
        assertEquals(-26.8083, r.lat());
    }

    @Test
    void ignoraElTextoQueLaAppAgregaAntesDelLink() {
        var r = service.resolver("Colombia 4695\nhttps://www.google.com/maps/@-26.7954738,-65.2568815,17z");
        assertEquals(-26.7954738, r.lat());
    }

    @Test
    void rechazaLinksQueNoSonDeGoogle() {
        assertEquals(LinkGoogleMapsService.NO_ES_LINK, service.resolver("https://evilgoogle.com/maps/@-26.79,-65.25,17z").error());
        assertEquals(LinkGoogleMapsService.NO_ES_LINK, service.resolver("Colombia 4695").error());
    }

    @Test
    void avisaSiElLinkNoTraeUbicacion() {
        assertEquals(LinkGoogleMapsService.SIN_UBICACION, service.resolver("https://www.google.com/maps/search/Colombia+4695").error());
    }

    @Test
    void rechazaUbicacionesFueraDeTucuman() {
        assertEquals(LinkGoogleMapsService.FUERA_DE_TUCUMAN, service.resolver("https://www.google.com/maps/@-34.6037,-58.3816,15z").error());
    }

    @Test
    void mismaCalleToleraAbreviaturasYRechazaOtraCalle() {
        assertTrue(GeocodingProxyService.mismaCalle("Colombia", "Colombia"));
        assertTrue(GeocodingProxyService.mismaCalle("av mate de luna", "Avenida Mate de Luna"));
        assertTrue(GeocodingProxyService.mismaCalle("Peron", "Avenida Presidente Perón"));
        assertFalse(GeocodingProxyService.mismaCalle("Colombia", "Avenida Mate de Luna"));
        assertFalse(GeocodingProxyService.mismaCalle("San Juan", "San Martín"));
    }
}
