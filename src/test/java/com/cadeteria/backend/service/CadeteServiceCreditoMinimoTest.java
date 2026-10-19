package com.cadeteria.backend.service;

import com.cadeteria.backend.common.BadRequestException;
import com.cadeteria.backend.common.ConflictException;
import com.cadeteria.backend.model.Cadete;
import com.cadeteria.backend.model.EstadoCadete;
import com.cadeteria.backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Crédito mínimo para estar disponible (2026-10-19): el cadete PORCENTAJE con menos que la comisión
 * del viaje más barato (precio base × %) no se puede poner Libre, y si se lo bajan desde el panel
 * estando Libre se lo saca al toque.
 */
class CadeteServiceCreditoMinimoTest {

    private CadeteService service;
    private ConfiguracionService config;
    private PedidoRepository pedidoRepo;
    private CadeteSesionService sesionService;
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
        config = mock(ConfiguracionService.class);
        pedidoRepo = mock(PedidoRepository.class);
        sesionService = mock(CadeteSesionService.class);
        service = new CadeteService(repo, mock(TipoVehiculoRepository.class), estadoRepo,
                mock(PasswordEncoder.class), mock(GeocodingService.class), sesionService,
                mock(WebSocketPublisher.class), mock(FcmService.class), pedidoRepo,
                mock(PedidoUbicacionRepository.class), mock(AvisoGeneralRepository.class),
                mock(AvisoGeneralLecturaRepository.class), config,
                mock(CadeteEstadoLogRepository.class), mock(MovimientoCreditoRepository.class), mock(MetricasService.class),
                mock(IncidenciaService.class), mock(PagoSemanalMovimientoRepository.class));

        when(config.getBigDecimal("precio_base_viaje", BigDecimal.valueOf(2000))).thenReturn(new BigDecimal("2000"));
        when(config.getBigDecimal("comision_porcentaje", BigDecimal.TEN)).thenReturn(new BigDecimal("25"));
        when(config.getInt(anyString(), anyInt())).thenAnswer(i -> i.getArgument(1));
        for (String id : new String[]{"LIBRE", "OCUPADO", "DESCONECTADO"}) {
            when(estadoRepo.findById(id)).thenReturn(Optional.of(estado(id)));
        }

        cadete = new Cadete();
        cadete.setId("c1");
        cadete.setUsername("30111222");
        cadete.setModalidadPago("PORCENTAJE");
        cadete.setCreditoDisponible(new BigDecimal("600.00"));
        cadete.setEstado(estado("LIBRE"));
        when(repo.findById("c1")).thenReturn(Optional.of(cadete));
        when(repo.findByIdParaActualizar("c1")).thenReturn(Optional.of(cadete));
        when(repo.findByUsername("30111222")).thenReturn(Optional.of(cadete));
        when(repo.save(any(Cadete.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void elMinimoEsLaComisionDelViajeMasBarato() {
        assertEquals(new BigDecimal("500.00"), CadeteService.creditoMinimo(config));
        assertNull(CadeteService.motivoSinCreditoParaLibre(cadete, config));
        cadete.setCreditoDisponible(new BigDecimal("100"));
        assertNotNull(CadeteService.motivoSinCreditoParaLibre(cadete, config));
        cadete.setModalidadPago("SEMANAL");
        assertNull(CadeteService.motivoSinCreditoParaLibre(cadete, config), "el semanal no usa crédito");
    }

    @Test
    void conMenosDelMinimoNoSePoneLibreNiLoPoneElAdmin() {
        cadete.setCreditoDisponible(new BigDecimal("100"));
        cadete.setEstado(estado("OCUPADO"));

        assertThrows(BadRequestException.class, () -> service.actualizarEstado("30111222", "LIBRE"));
        assertThrows(ConflictException.class, () -> service.marcarLibre("c1"));
        assertEquals("OCUPADO", cadete.getEstado().getId());
    }

    @Test
    void siLeBajanElCreditoSinViajesQuedaDesconectado() {
        when(pedidoRepo.countByCadeteAsignadoIdAndEstadoIdIn(anyString(), any())).thenReturn(0L);

        service.ajustarCredito("c1", new BigDecimal("300"), false, "Diferencia de comisión", "admin");

        assertEquals("DESCONECTADO", cadete.getEstado().getId());
        verify(sesionService).cerrar(cadete);
    }

    @Test
    void siLeBajanElCreditoConUnViajeQuedaOcupado() {
        when(pedidoRepo.countByCadeteAsignadoIdAndEstadoIdIn(anyString(), any())).thenReturn(1L);

        service.ajustarCredito("c1", new BigDecimal("300"), false, "Diferencia de comisión", "admin");

        assertEquals("OCUPADO", cadete.getEstado().getId());
    }

    @Test
    void siLeSigueAlcanzandoNoSeLoToca() {
        service.ajustarCredito("c1", new BigDecimal("50"), false, "Ajuste", "admin");
        assertEquals("LIBRE", cadete.getEstado().getId());
    }
}
