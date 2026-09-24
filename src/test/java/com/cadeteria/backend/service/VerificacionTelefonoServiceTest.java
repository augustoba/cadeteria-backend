package com.cadeteria.backend.service;

import com.cadeteria.backend.common.BadRequestException;
import com.cadeteria.backend.config.AppProperties;
import com.cadeteria.backend.dto.VerificacionTelefonoDtos.EnviarCodigoResponse;
import com.cadeteria.backend.model.TelefonoValidado;
import com.cadeteria.backend.model.VerificacionTelefono;
import com.cadeteria.backend.repository.TelefonoValidadoRepository;
import com.cadeteria.backend.repository.VerificacionTelefonoRepository;
import com.cadeteria.backend.util.TelefonoUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Transporte y degradación del código de "/pedir" (spec-antiabuso-pedidos-publicos.md, Fase 2
 * y §6): WhatsApp → SMS → sin verificar, y la lista blanca de teléfonos ya validados.
 */
class VerificacionTelefonoServiceTest {

    private static final String TEL = "3815551122";

    private VerificacionTelefonoRepository repo;
    private TelefonoValidadoRepository validadoRepo;
    private SmsGatewayService sms;
    private WhatsappGatewayService whatsapp;
    private ConfiguracionService config;
    private AppProperties props;
    private VerificacionTelefonoService service;

    @BeforeEach
    void setUp() {
        repo = mock(VerificacionTelefonoRepository.class);
        validadoRepo = mock(TelefonoValidadoRepository.class);
        sms = mock(SmsGatewayService.class);
        whatsapp = mock(WhatsappGatewayService.class);
        config = mock(ConfiguracionService.class);
        RateLimitService rateLimit = mock(RateLimitService.class);
        props = new AppProperties();
        when(rateLimit.permitir(anyString(), any(Integer.class), any())).thenReturn(true);
        when(config.getBoolean("verificacion_recordar_telefonos", true)).thenReturn(true);
        when(repo.save(any(VerificacionTelefono.class))).thenAnswer(i -> i.getArgument(0));
        service = new VerificacionTelefonoService(repo, validadoRepo, sms, whatsapp, rateLimit, config, props);
    }

    @Test
    void conGatewayConectadoMandaPorWhatsapp() {
        when(whatsapp.enviarYa(anyString(), anyString())).thenReturn(true);

        EnviarCodigoResponse r = service.enviarCodigo(TEL);

        assertEquals(VerificacionTelefonoService.CODIGO_WHATSAPP, r.resultado());
        assertNull(r.token());
        verify(sms, never()).enviar(anyString(), anyString());
    }

    @Test
    void sinWhatsappCaeAlSms() {
        when(whatsapp.enviarYa(anyString(), anyString())).thenReturn(false);
        when(sms.isHabilitado()).thenReturn(true);

        EnviarCodigoResponse r = service.enviarCodigo(TEL);

        assertEquals(VerificacionTelefonoService.CODIGO_SMS, r.resultado());
        verify(sms).enviar(anyString(), anyString());
    }

    @Test
    void transporteSmsNiPruebaWhatsapp() {
        props.getVerificacion().setTransporte("SMS");
        when(sms.isHabilitado()).thenReturn(true);

        service.enviarCodigo(TEL);

        verify(whatsapp, never()).enviarYa(anyString(), anyString());
    }

    @Test
    void sinNingunMedioDevuelveTokenSinVerificarYLaSolicitudQuedaMarcada() {
        when(whatsapp.enviarYa(anyString(), anyString())).thenReturn(false);
        when(sms.isHabilitado()).thenReturn(false);

        EnviarCodigoResponse r = service.enviarCodigo(TEL);

        assertEquals(VerificacionTelefonoService.SIN_VERIFICAR, r.resultado());
        assertNotNull(r.token());
        VerificacionTelefono emitido = ultimoGuardado();
        when(repo.findByToken(r.token())).thenReturn(Optional.of(emitido));
        assertTrue(service.consumirToken(r.token(), TEL));
    }

    @Test
    void telefonoYaValidadoNoPideCodigo() {
        when(validadoRepo.existsById(TelefonoUtils.normalizar(TEL))).thenReturn(true);

        EnviarCodigoResponse r = service.enviarCodigo(TEL);

        assertEquals(VerificacionTelefonoService.YA_VALIDADO, r.resultado());
        assertNotNull(r.token());
        verify(whatsapp, never()).enviarYa(anyString(), anyString());
        VerificacionTelefono emitido = ultimoGuardado();
        when(repo.findByToken(r.token())).thenReturn(Optional.of(emitido));
        assertFalse(service.consumirToken(r.token(), TEL));
    }

    @Test
    void conLaListaBlancaApagadaPideCodigoIgual() {
        when(config.getBoolean("verificacion_recordar_telefonos", true)).thenReturn(false);
        when(validadoRepo.existsById(anyString())).thenReturn(true);
        when(whatsapp.enviarYa(anyString(), anyString())).thenReturn(true);

        assertEquals(VerificacionTelefonoService.CODIGO_WHATSAPP, service.enviarCodigo(TEL).resultado());
    }

    @Test
    void verificarBienSumaElTelefonoALaListaBlanca() {
        VerificacionTelefono v = new VerificacionTelefono();
        v.setTelefono(TelefonoUtils.normalizar(TEL));
        v.setCodigo("123456");
        v.setVia("WHATSAPP");
        v.setExpiraEn(java.time.Instant.now().plusSeconds(300));
        when(repo.findTopByTelefonoAndVerificadoEnIsNullOrderByCreadoEnDesc(TelefonoUtils.normalizar(TEL)))
                .thenReturn(Optional.of(v));

        service.verificarCodigo(TEL, "123456");

        ArgumentCaptor<TelefonoValidado> captor = ArgumentCaptor.forClass(TelefonoValidado.class);
        verify(validadoRepo).save(captor.capture());
        assertEquals("WHATSAPP", captor.getValue().getVia());
    }

    @Test
    void tokenDeOtroTelefonoNoSirve() {
        VerificacionTelefono v = new VerificacionTelefono();
        v.setTelefono(TelefonoUtils.normalizar(TEL));
        v.setVerificadoEn(java.time.Instant.now());
        v.setToken("tok");
        when(repo.findByToken(eq("tok"))).thenReturn(Optional.of(v));

        assertThrows(BadRequestException.class, () -> service.consumirToken("tok", "3819999999"));
    }

    private VerificacionTelefono ultimoGuardado() {
        ArgumentCaptor<VerificacionTelefono> captor = ArgumentCaptor.forClass(VerificacionTelefono.class);
        verify(repo, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        return captor.getValue();
    }
}
