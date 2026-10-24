package com.cadeteria.backend.service;

import com.cadeteria.backend.common.ConflictException;
import com.cadeteria.backend.config.AppProperties;
import com.cadeteria.backend.dto.PedidoDtos.PedidoRequest;
import com.cadeteria.backend.model.EstadoPedido;
import com.cadeteria.backend.model.Pedido;
import com.cadeteria.backend.model.PedidoComentario;
import com.cadeteria.backend.model.PedidoPrecioLog;
import com.cadeteria.backend.repository.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Editar un pedido mal cargado (2026-10-24): solo sin asignar, y queda anotado qué cambió. */
class PedidoServiceEditarTest {

    private PedidoService service;
    private PedidoComentarioRepository comentarioRepo;
    private PedidoPrecioLogRepository precioLogRepo;
    private Pedido pedido;

    @BeforeEach
    void setUp() {
        PedidoRepository repo = mock(PedidoRepository.class);
        EstadoPedidoRepository estadoRepo = mock(EstadoPedidoRepository.class);
        comentarioRepo = mock(PedidoComentarioRepository.class);
        precioLogRepo = mock(PedidoPrecioLogRepository.class);
        ConfiguracionService config = mock(ConfiguracionService.class);
        service = new PedidoService(
                repo, mock(CadeteRepository.class), estadoRepo, mock(ResultadoOfertaRepository.class),
                mock(OfertaPedidoRepository.class), mock(EstadoCadeteRepository.class), config,
                mock(WebSocketPublisher.class), mock(FcmService.class), mock(SmsGatewayService.class),
                mock(PedidoUbicacionRepository.class), comentarioRepo,
                precioLogRepo, mock(WebPushService.class),
                mock(PedidoParadaRepository.class), mock(MovimientoCreditoRepository.class),
                mock(IncidenciaRepository.class), mock(PedidoCadeteExcluidoRepository.class), new AppProperties());
        ReflectionTestUtils.setField(service, "em", mock(EntityManager.class));
        when(config.getBigDecimal(anyString(), any())).thenAnswer(i -> i.getArgument(1));
        when(estadoRepo.findById(anyString())).thenAnswer(i -> Optional.of(estado(i.getArgument(0))));

        pedido = new Pedido();
        pedido.setId("p1");
        pedido.setNumero(1500200L);
        pedido.setEstado(estado("SIN_ASIGNAR"));
        pedido.setClienteTelefono("3794111111");
        pedido.setClienteNombre("Ana");
        pedido.setOrigenDireccion("Junin 1000");
        pedido.setDestinoDireccion("Mendoza 500");
        pedido.setPrecio(new BigDecimal("1500.00"));
        when(repo.findByIdParaActualizar("p1")).thenReturn(Optional.of(pedido));
        when(repo.save(any(Pedido.class))).thenAnswer(i -> i.getArgument(0));
    }

    private static EstadoPedido estado(String id) {
        EstadoPedido e = new EstadoPedido();
        e.setId(id);
        return e;
    }

    private static PedidoRequest request(String telefono, BigDecimal precio) {
        return new PedidoRequest(
                telefono, "Ana", "Junin 1000", -27.46, -58.83, "Mendoza 500", -27.47, -58.84,
                precio, null, false, null, null, null, null, null, null, null, null, false,
                false, null, null, null, null, false, null);
    }

    @Test
    void corrigeElTelefonoYLoAnotaEnLosComentarios() {
        service.editar("p1", request("3794222222", new BigDecimal("1500")), "operador");

        assertEquals("3794222222", pedido.getClienteTelefono());
        assertEquals("SIN_ASIGNAR", pedido.getEstado().getId());
        ArgumentCaptor<PedidoComentario> c = ArgumentCaptor.forClass(PedidoComentario.class);
        verify(comentarioRepo).save(c.capture());
        assertEquals("operador", c.getValue().getAdminUsername());
        assertTrue(c.getValue().getTexto().contains("teléfono 3794111111 → 3794222222"), c.getValue().getTexto());
        verify(precioLogRepo, never()).save(any());
    }

    @Test
    void cambiarElPrecioQuedaEnSuHistorial() {
        service.editar("p1", request("3794111111", new BigDecimal("2000")), "operador");

        ArgumentCaptor<PedidoPrecioLog> log = ArgumentCaptor.forClass(PedidoPrecioLog.class);
        verify(precioLogRepo).save(log.capture());
        assertEquals(new BigDecimal("1500.00"), log.getValue().getPrecioAnterior());
        assertEquals(new BigDecimal("2000"), log.getValue().getPrecioNuevo());
    }

    @Test
    void sinCambiosNoDejaComentario() {
        service.editar("p1", request("3794111111", new BigDecimal("1500")), "operador");

        verify(comentarioRepo, never()).save(any());
    }

    @Test
    void conCadeteYaNoSePuedeEditar() {
        for (String estado : List.of("PENDIENTE", "EN_CURSO", "FINALIZADO", "CANCELADO", "NO_ENTREGADO")) {
            pedido.setEstado(estado(estado));
            assertThrows(ConflictException.class,
                    () -> service.editar("p1", request("3794222222", new BigDecimal("1500")), "operador"), estado);
        }
        assertEquals("3794111111", pedido.getClienteTelefono());
    }
}
