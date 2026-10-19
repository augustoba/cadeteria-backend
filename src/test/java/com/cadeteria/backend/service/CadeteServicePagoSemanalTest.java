package com.cadeteria.backend.service;

import com.cadeteria.backend.common.BadRequestException;
import com.cadeteria.backend.model.Cadete;
import com.cadeteria.backend.model.PagoSemanalMovimiento;
import com.cadeteria.backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pago semanal (2026-10-19): "Registrar otro pago" suma (antes reemplazaba) y un pago equivocado de
 * esta semana se anula recalculando si puede trabajar.
 */
class CadeteServicePagoSemanalTest {

    private CadeteService service;
    private Cadete cadete;
    private final List<PagoSemanalMovimiento> guardados = new ArrayList<>();

    @BeforeEach
    void setUp() {
        CadeteRepository repo = mock(CadeteRepository.class);
        PagoSemanalMovimientoRepository pagosRepo = mock(PagoSemanalMovimientoRepository.class);
        PedidoRepository pedidoRepo = mock(PedidoRepository.class);
        ConfiguracionService config = mock(ConfiguracionService.class);
        service = new CadeteService(repo, mock(TipoVehiculoRepository.class), mock(EstadoCadeteRepository.class),
                mock(PasswordEncoder.class), mock(GeocodingService.class), mock(CadeteSesionService.class),
                mock(WebSocketPublisher.class), mock(FcmService.class), pedidoRepo,
                mock(PedidoUbicacionRepository.class), mock(AvisoGeneralRepository.class),
                mock(AvisoGeneralLecturaRepository.class), config,
                mock(CadeteEstadoLogRepository.class), mock(MovimientoCreditoRepository.class), mock(MetricasService.class),
                mock(IncidenciaService.class), pagosRepo);

        cadete = new Cadete();
        cadete.setId("c1");
        cadete.setModalidadPago("SEMANAL");
        cadete.setMontoSemanalActual(new BigDecimal("5000"));
        when(repo.findById("c1")).thenReturn(Optional.of(cadete));
        when(repo.save(any(Cadete.class))).thenAnswer(i -> i.getArgument(0));
        when(pagosRepo.save(any(PagoSemanalMovimiento.class))).thenAnswer(i -> {
            PagoSemanalMovimiento m = i.getArgument(0);
            if (!guardados.contains(m)) guardados.add(0, m);
            return m;
        });
        when(pagosRepo.findByCadeteIdAndSemanaInicioOrderByCreadoEnDesc(anyString(), any()))
                .thenAnswer(i -> new ArrayList<>(guardados));
    }

    private Instant manana() {
        return Instant.now().plusSeconds(86_400);
    }

    @Test
    void otroPagoSeSumaALoYaPagado() {
        service.habilitarPagoSemanal("c1", new BigDecimal("2000"), manana(), null, "admin");
        assertEquals(new BigDecimal("2000"), cadete.getPagoSemanalMontoPagado());
        assertNotNull(cadete.getPagoSemanalVenceEn());

        service.habilitarPagoSemanal("c1", new BigDecimal("3000"), null, null, "admin");
        assertEquals(new BigDecimal("5000"), cadete.getPagoSemanalMontoPagado());
        assertNull(cadete.getPagoSemanalVenceEn(), "completó la cuota");
        assertTrue(cadete.isHabilitadoPago());
        assertEquals(2, guardados.size());
        assertEquals("admin", guardados.get(0).getCreadoPorUsername());
    }

    @Test
    void loPagadoAntesDelHistorialNoSePierde() {
        cadete.setHabilitadoPago(true);
        cadete.setPagoSemanalMontoPagado(new BigDecimal("2000"));
        cadete.setPagoSemanalVenceEn(manana());

        service.habilitarPagoSemanal("c1", new BigDecimal("3000"), null, null, "admin");

        assertEquals(new BigDecimal("5000"), cadete.getPagoSemanalMontoPagado());
    }

    @Test
    void anularUnPagoDejaElParcial() {
        service.habilitarPagoSemanal("c1", new BigDecimal("2000"), manana(), null, "admin");
        service.habilitarPagoSemanal("c1", new BigDecimal("3000"), null, null, "admin");
        String segundo = guardados.get(0).getId();

        service.anularPagoSemanal("c1", segundo, "Era de otro cadete", manana(), "admin");

        assertEquals(new BigDecimal("2000"), cadete.getPagoSemanalMontoPagado());
        assertNotNull(cadete.getPagoSemanalVenceEn());
        assertTrue(cadete.isHabilitadoPago());
        assertEquals("Era de otro cadete", guardados.get(0).getMotivoAnulacion());
    }

    @Test
    void parcialSinVencimientoPideLaFecha() {
        service.habilitarPagoSemanal("c1", new BigDecimal("5000"), null, null, "admin");
        service.habilitarPagoSemanal("c1", new BigDecimal("1000"), null, null, "admin");
        String elDe5000 = guardados.get(1).getId();

        assertThrows(BadRequestException.class, () -> service.anularPagoSemanal("c1", elDe5000, "Mal cargado", null, "admin"));
    }

    @Test
    void anularElUnicoPagoLoDeshabilita() {
        service.habilitarPagoSemanal("c1", new BigDecimal("5000"), null, null, "admin");

        service.anularPagoSemanal("c1", guardados.get(0).getId(), "Mal cargado", null, "admin");

        assertFalse(cadete.isHabilitadoPago());
        assertNull(cadete.getPagoSemanalMontoPagado());
    }

    @Test
    void soloSeAnulanLosDeEstaSemana() {
        assertThrows(BadRequestException.class, () -> service.anularPagoSemanal("c1", "de-otra-semana", "x", null, "admin"));
    }
}
