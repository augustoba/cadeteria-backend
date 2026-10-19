package com.cadeteria.backend.service;

import com.cadeteria.backend.common.BadRequestException;
import com.cadeteria.backend.common.ConflictException;
import com.cadeteria.backend.model.Cadete;
import com.cadeteria.backend.model.MovimientoCredito;
import com.cadeteria.backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Corregir el crédito (2026-10-19): una carga equivocada se anula con un movimiento contrario (no se
 * edita ni se borra) y el ajuste a mano suma o resta con motivo. Los dos pueden dejarlo en negativo.
 */
class CadeteServiceCorreccionCreditoTest {

    private CadeteService service;
    private MovimientoCreditoRepository movimientoRepo;
    private Cadete cadete;
    private MovimientoCredito carga;

    @BeforeEach
    void setUp() {
        CadeteRepository repo = mock(CadeteRepository.class);
        movimientoRepo = mock(MovimientoCreditoRepository.class);
        service = new CadeteService(repo, mock(TipoVehiculoRepository.class), mock(EstadoCadeteRepository.class),
                mock(PasswordEncoder.class), mock(GeocodingService.class), mock(CadeteSesionService.class),
                mock(WebSocketPublisher.class), mock(FcmService.class), mock(PedidoRepository.class),
                mock(PedidoUbicacionRepository.class), mock(AvisoGeneralRepository.class),
                mock(AvisoGeneralLecturaRepository.class), mock(ConfiguracionService.class),
                mock(CadeteEstadoLogRepository.class), movimientoRepo, mock(MetricasService.class),
                mock(IncidenciaService.class), mock(PagoSemanalMovimientoRepository.class));

        cadete = new Cadete();
        cadete.setId("c1");
        cadete.setModalidadPago("PORCENTAJE");
        cadete.setCreditoDisponible(new BigDecimal("9800.00"));
        when(repo.findByIdParaActualizar("c1")).thenReturn(Optional.of(cadete));

        carga = new MovimientoCredito();
        carga.setId("m1");
        carga.setCadeteId("c1");
        carga.setTipo("ACREDITACION");
        carga.setMonto(new BigDecimal("5000.00"));
        when(movimientoRepo.findById("m1")).thenReturn(Optional.of(carga));
    }

    @Test
    void anularRestaLaCargaYLaDejaMarcada() {
        service.anularAcreditacion("c1", "m1", "Cargué 5000 en vez de 500", "admin");

        assertEquals(new BigDecimal("4800.00"), cadete.getCreditoDisponible());
        ArgumentCaptor<MovimientoCredito> guardados = ArgumentCaptor.forClass(MovimientoCredito.class);
        verify(movimientoRepo, atLeastOnce()).save(guardados.capture());
        MovimientoCredito anulacion = guardados.getAllValues().stream()
                .filter(m -> "ANULACION".equals(m.getTipo())).findFirst().orElseThrow();
        assertEquals(new BigDecimal("5000.00"), anulacion.getMonto());
        assertEquals("m1", anulacion.getAnulaMovimientoId());
        assertEquals("Cargué 5000 en vez de 500", anulacion.getMotivo());
        assertEquals("admin", anulacion.getCreadoPorUsername());
        assertEquals(anulacion.getId(), carga.getAnuladoPorId());
    }

    @Test
    void noSeAnulaDosVecesNiOtraCosaQueUnaCarga() {
        carga.setAnuladoPorId("otra");
        assertThrows(ConflictException.class, () -> service.anularAcreditacion("c1", "m1", "x", "admin"));

        carga.setAnuladoPorId(null);
        carga.setTipo("COMISION");
        assertThrows(BadRequestException.class, () -> service.anularAcreditacion("c1", "m1", "x", "admin"));
    }

    @Test
    void ajusteRestaConMotivoYPuedeQuedarEnNegativo() {
        cadete.setCreditoDisponible(new BigDecimal("100.00"));
        service.ajustarCredito("c1", new BigDecimal("300"), false, "Diferencia de comisión pedido 1500108", "admin");

        assertEquals(new BigDecimal("-200.00"), cadete.getCreditoDisponible());
        ArgumentCaptor<MovimientoCredito> guardado = ArgumentCaptor.forClass(MovimientoCredito.class);
        verify(movimientoRepo, atLeastOnce()).save(guardado.capture());
        assertEquals("AJUSTE_RESTA", guardado.getValue().getTipo());
        assertEquals("Diferencia de comisión pedido 1500108", guardado.getValue().getMotivo());
    }

    @Test
    void ajusteSuma() {
        service.ajustarCredito("c1", new BigDecimal("200"), true, "Devolución", "admin");
        assertEquals(new BigDecimal("10000.00"), cadete.getCreditoDisponible());
        verify(movimientoRepo, atLeastOnce()).save(any());
    }
}
