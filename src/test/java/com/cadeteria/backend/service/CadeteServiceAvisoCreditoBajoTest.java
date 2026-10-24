package com.cadeteria.backend.service;

import com.cadeteria.backend.model.Cadete;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Crédito bajo (2026-10-24): avisa al quedar por debajo y se repite cada X minutos con la app abierta. */
class CadeteServiceAvisoCreditoBajoTest {

    private ConfiguracionService config;
    private FcmService fcm;
    private Cadete cadete;
    private final Instant ahora = Instant.parse("2026-10-24T15:00:00Z");

    @BeforeEach
    void setUp() {
        config = mock(ConfiguracionService.class);
        fcm = mock(FcmService.class);
        when(config.getBigDecimal(eq("credito_bajo_alerta_umbral"), any())).thenReturn(new BigDecimal("500"));
        when(config.getInt(eq("credito_bajo_repetir_min"), any(Integer.class))).thenReturn(60);
        cadete = new Cadete();
        cadete.setModalidadPago("PORCENTAJE");
        cadete.setCreditoDisponible(new BigDecimal("300"));
        cadete.setFcmToken("tok");
        cadete.setUbicacionActualizadaEn(ahora.minus(Duration.ofMinutes(2)));
    }

    private void avisoEnviado(int veces) {
        verify(fcm, times(veces)).enviar(eq("tok"), eq("Crédito bajo"), anyString(), any());
    }

    @Test
    void alQuedarPorDebajoAvisaUnaVez() {
        assertTrue(CadeteService.avisarCreditoBajo(cadete, config, fcm, false, ahora));
        assertFalse(CadeteService.avisarCreditoBajo(cadete, config, fcm, false, ahora.plusSeconds(10)));
        avisoEnviado(1);
        assertTrue(cadete.isAlertaCreditoBajoEnviada());
    }

    @Test
    void conLaAppAbiertaLoRepiteCuandoPasaElTiempo() {
        CadeteService.avisarCreditoBajo(cadete, config, fcm, false, ahora);
        cadete.setUbicacionActualizadaEn(ahora.plus(Duration.ofMinutes(59)));
        assertFalse(CadeteService.avisarCreditoBajo(cadete, config, fcm, true, ahora.plus(Duration.ofMinutes(59))));
        cadete.setUbicacionActualizadaEn(ahora.plus(Duration.ofMinutes(60)));
        assertTrue(CadeteService.avisarCreditoBajo(cadete, config, fcm, true, ahora.plus(Duration.ofMinutes(60))));
        avisoEnviado(2);
    }

    @Test
    void sinGpsHaceRatoNoLeRepite() {
        CadeteService.avisarCreditoBajo(cadete, config, fcm, false, ahora);
        // Tocó Salir: la última ubicación quedó de hace 2 horas.
        assertFalse(CadeteService.avisarCreditoBajo(cadete, config, fcm, true, ahora.plus(Duration.ofHours(2))));
        avisoEnviado(1);
    }

    @Test
    void enCeroMinutosNoRepite() {
        when(config.getInt(eq("credito_bajo_repetir_min"), any(Integer.class))).thenReturn(0);
        CadeteService.avisarCreditoBajo(cadete, config, fcm, false, ahora);
        cadete.setUbicacionActualizadaEn(ahora.plus(Duration.ofHours(3)));
        assertFalse(CadeteService.avisarCreditoBajo(cadete, config, fcm, true, ahora.plus(Duration.ofHours(3))));
        avisoEnviado(1);
    }

    @Test
    void alCargarCreditoSeLimpiaParaVolverAAvisar() {
        CadeteService.avisarCreditoBajo(cadete, config, fcm, false, ahora);
        cadete.setCreditoDisponible(new BigDecimal("2000"));
        assertTrue(CadeteService.avisarCreditoBajo(cadete, config, fcm, true, ahora.plusSeconds(60)));
        assertFalse(cadete.isAlertaCreditoBajoEnviada());
        assertNull(cadete.getUltimoAvisoCreditoBajoEn());
        avisoEnviado(1);
    }

    @Test
    void elSemanalNoTieneCredito() {
        cadete.setModalidadPago("SEMANAL");
        assertFalse(CadeteService.avisarCreditoBajo(cadete, config, fcm, false, ahora));
        verify(fcm, never()).enviar(any(), any(), any(), any());
    }

    @Test
    void elTextoLlevaElSaldo() {
        CadeteService.avisarCreditoBajo(cadete, config, fcm, false, ahora);
        verify(fcm).enviar(eq("tok"), eq("Crédito bajo"),
                eq("Te queda poco crédito ($300). Cargá con la cadetería para seguir recibiendo viajes."), any());
        assertEquals(ahora, cadete.getUltimoAvisoCreditoBajoEn());
    }
}
