package com.cadeteria.backend.service;

import com.cadeteria.backend.common.BadRequestException;
import com.cadeteria.backend.config.AppProperties;
import com.cadeteria.backend.dto.PedidoDtos.FinalizarRequest;
import com.cadeteria.backend.model.Cadete;
import com.cadeteria.backend.model.EstadoPedido;
import com.cadeteria.backend.model.Pedido;
import com.cadeteria.backend.model.PedidoComentario;
import com.cadeteria.backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Fotos de retiro/entrega configurables (spec-app-mejoras-visuales §6) y la válvula de escape
 * de la cola offline de la app: si el archivo local de la foto se perdió antes de subirse, el
 * viaje igual se registra (con un comentario automático) en vez de quedar trabado para siempre.
 */
class PedidoServiceComprobantesTest {

    private PedidoRepository repo;
    private CadeteRepository cadeteRepo;
    private ConfiguracionService config;
    private PedidoComentarioRepository comentarioRepo;
    private PedidoService service;
    private Pedido pedido;

    @BeforeEach
    void setUp() {
        repo = mock(PedidoRepository.class);
        // La lectura con bloqueo (2026-09-26) devuelve lo mismo que findById en estos tests.
        when(repo.findByIdParaActualizar(org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(inv -> repo.findById(inv.getArgument(0)));
        cadeteRepo = mock(CadeteRepository.class);
        config = mock(ConfiguracionService.class);
        comentarioRepo = mock(PedidoComentarioRepository.class);
        service = new PedidoService(
                repo, cadeteRepo, mock(EstadoPedidoRepository.class), mock(ResultadoOfertaRepository.class),
                mock(OfertaPedidoRepository.class), mock(EstadoCadeteRepository.class), config,
                mock(WebSocketPublisher.class), mock(FcmService.class), mock(SmsGatewayService.class),
                mock(PedidoUbicacionRepository.class), comentarioRepo,
                mock(PedidoPrecioLogRepository.class), mock(WebPushService.class),
                mock(PedidoParadaRepository.class), mock(MovimientoCreditoRepository.class),
                mock(IncidenciaRepository.class), mock(PedidoCadeteExcluidoRepository.class), new AppProperties());
        org.springframework.test.util.ReflectionTestUtils.setField(service, "em",
                mock(jakarta.persistence.EntityManager.class));

        Cadete cadete = new Cadete();
        cadete.setId("c1");
        cadete.setUsername("30111222");
        when(cadeteRepo.findByUsername("30111222")).thenReturn(Optional.of(cadete));

        EstadoPedido enCurso = new EstadoPedido();
        enCurso.setId("EN_CURSO");
        pedido = new Pedido();
        pedido.setId("p1");
        pedido.setEstado(enCurso);
        pedido.setCadeteAsignado(cadete);
        when(repo.findById("p1")).thenReturn(Optional.of(pedido));
        when(repo.save(any(Pedido.class))).thenAnswer(i -> i.getArgument(0));

        when(config.getBoolean("foto_entrega_obligatoria", true)).thenReturn(true);
    }

    @Test
    void retiroSinFotoPasaSiNoEsObligatoria() {
        assertDoesNotThrow(() -> service.registrarRecepcion("p1", "30111222", null, null, null, false));
        assertNotNull(pedido.getRetiradoEn());
    }

    @Test
    void retiroSinFotoFallaSiEsObligatoria() {
        when(config.getBoolean("foto_retiro_obligatoria", false)).thenReturn(true);
        assertThrows(BadRequestException.class,
                () -> service.registrarRecepcion("p1", "30111222", null, null, null, false));
    }

    @Test
    void retiroConArchivoPerdidoPasaAunqueSeaObligatoriaYDejaComentario() {
        when(config.getBoolean("foto_retiro_obligatoria", false)).thenReturn(true);
        service.registrarRecepcion("p1", "30111222", null, null, null, true);
        assertNotNull(pedido.getRetiradoEn());
        verify(comentarioRepo).save(any(PedidoComentario.class));
    }

    @Test
    void finalizarSinFotoFallaConElDefault() {
        assertThrows(BadRequestException.class, () -> service.finalizar("p1", "30111222",
                new FinalizarRequest("Lucía", null, null, null, null, null, null)));
    }

    @Test
    void finalizarSinReceptorFallaSiempreAunqueSeHayaPerdidoLaFoto() {
        assertThrows(BadRequestException.class, () -> service.finalizar("p1", "30111222",
                new FinalizarRequest(null, null, null, null, null, true, null)));
        verify(comentarioRepo, never()).save(any(PedidoComentario.class));
    }
}
