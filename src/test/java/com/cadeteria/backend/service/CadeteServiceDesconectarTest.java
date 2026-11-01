package com.cadeteria.backend.service;

import com.cadeteria.backend.common.ConflictException;
import com.cadeteria.backend.model.Cadete;
import com.cadeteria.backend.model.EstadoCadete;
import com.cadeteria.backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Desconectar a un cadete desde el panel (2026-10-31): los que cierran la app quedan Libres y les llegan pedidos. */
class CadeteServiceDesconectarTest {

    private CadeteService service;
    private PedidoRepository pedidoRepo;
    private CadeteSesionService sesionService;
    private FcmService fcm;
    private Cadete cadete;

    private static EstadoCadete estado(String id) {
        EstadoCadete e = new EstadoCadete();
        e.setId(id);
        return e;
    }

    @BeforeEach
    void setUp() {
        CadeteRepository repo = mock(CadeteRepository.class);
        EstadoCadeteRepository estadoRepo = mock(EstadoCadeteRepository.class);
        pedidoRepo = mock(PedidoRepository.class);
        sesionService = mock(CadeteSesionService.class);
        fcm = mock(FcmService.class);
        service = new CadeteService(repo, mock(TipoVehiculoRepository.class), estadoRepo,
                mock(PasswordEncoder.class), mock(GeocodingService.class), sesionService,
                mock(WebSocketPublisher.class), fcm, pedidoRepo,
                mock(PedidoUbicacionRepository.class), mock(AvisoGeneralRepository.class),
                mock(AvisoGeneralLecturaRepository.class), mock(ConfiguracionService.class),
                mock(CadeteEstadoLogRepository.class), mock(MovimientoCreditoRepository.class), mock(MetricasService.class),
                mock(IncidenciaService.class), mock(PagoSemanalMovimientoRepository.class));

        when(estadoRepo.findById("DESCONECTADO")).thenReturn(Optional.of(estado("DESCONECTADO")));
        cadete = new Cadete();
        cadete.setId("c1");
        cadete.setFcmToken("fcm");
        cadete.setSessionToken("sesion");
        cadete.setEstado(estado("LIBRE"));
        when(repo.findById("c1")).thenReturn(Optional.of(cadete));
        when(repo.save(any(Cadete.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void sinViajesQuedaDesconectadoConLaSesionCortadaYElAviso() {
        service.desconectarDesdePanel("c1");

        assertEquals("DESCONECTADO", cadete.getEstado().getId());
        assertNotEquals("sesion", cadete.getSessionToken());
        verify(sesionService).cerrar(cadete);
        verify(fcm).enviar(eq("fcm"), eq("Te desconectamos de la app"),
                eq("Para volver a recibir pedidos, iniciá sesión y ponete Libre."), any());
    }

    @Test
    void conViajesUOfertasNoSeLoDesconecta() {
        when(pedidoRepo.countByCadeteAsignadoIdAndEstadoIdIn(eq("c1"), any())).thenReturn(2L);

        ConflictException e = assertThrows(ConflictException.class, () -> service.desconectarDesdePanel("c1"));

        assertEquals("No se lo puede desconectar: tiene 2 viajes sin terminar u ofrecidos.", e.getMessage());
        assertEquals("LIBRE", cadete.getEstado().getId());
        assertEquals("sesion", cadete.getSessionToken());
        verify(fcm, never()).enviar(any(), any(), any(), any());
    }

    @Test
    void siYaEstabaDesconectadoIgualLeCortaLaSesionSinCerrarOtraVezLasHoras() {
        cadete.setEstado(estado("DESCONECTADO"));

        service.desconectarDesdePanel("c1");

        assertNotEquals("sesion", cadete.getSessionToken());
        verify(sesionService, never()).cerrar(any());
    }
}
