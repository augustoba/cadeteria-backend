package com.cadeteria.backend.service;

import com.cadeteria.backend.model.EstadoPedido;
import com.cadeteria.backend.model.Pedido;
import com.cadeteria.backend.repository.ChatMensajeRepository;
import com.cadeteria.backend.repository.PedidoRepository;
import com.cadeteria.backend.repository.WhatsappMensajeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Purga de imágenes de pedidos terminados (mejora 2026-09-23) — nunca debe tocar un pedido
 * que no esté en la lista de candidatos que devuelve el repositorio (ese filtro, incluyendo
 * "nunca un pedido abierto", vive en PedidoRepository.findConImagenesTerminadosAntesDe).
 */
class RetencionDatosServiceTest {

    private PedidoRepository pedidoRepo;
    private ConfiguracionService configuracionService;
    private CloudinaryService cloudinaryService;
    private RetencionDatosService service;

    @BeforeEach
    void setUp() {
        pedidoRepo = mock(PedidoRepository.class);
        configuracionService = mock(ConfiguracionService.class);
        cloudinaryService = mock(CloudinaryService.class);
        service = new RetencionDatosService(mock(WhatsappMensajeRepository.class), mock(ChatMensajeRepository.class),
                pedidoRepo, configuracionService, cloudinaryService);
    }

    @Test
    void desactivadaPorDefectoNoTocaNada() {
        when(configuracionService.getInt(eq("retencion_imagenes_pedido_dias"), anyInt())).thenReturn(0);

        service.purgarMensajesViejos();

        verify(pedidoRepo, never()).findConImagenesTerminadosAntesDe(any());
        verify(pedidoRepo, never()).saveAll(any());
    }

    @Test
    void limpiaLasTresFotosYPideElBorradoEnCloudinaryDeCadaUna() {
        when(configuracionService.getInt(eq("retencion_imagenes_pedido_dias"), anyInt())).thenReturn(60);
        Pedido p = pedidoTerminado();
        p.setFotoRecepcionUrl("https://res.cloudinary.com/demo/image/upload/v1/recepcion.jpg");
        p.setEntregaFotoUrl("https://res.cloudinary.com/demo/image/upload/v1/entrega.jpg");
        p.setFirmaReceptorUrl("https://res.cloudinary.com/demo/image/upload/v1/firma.jpg");
        when(pedidoRepo.findConImagenesTerminadosAntesDe(any())).thenReturn(List.of(p));

        service.purgarMensajesViejos();

        assertNull(p.getFotoRecepcionUrl());
        assertNull(p.getEntregaFotoUrl());
        assertNull(p.getFirmaReceptorUrl());
        verify(cloudinaryService).borrarSiCorresponde("https://res.cloudinary.com/demo/image/upload/v1/recepcion.jpg");
        verify(cloudinaryService).borrarSiCorresponde("https://res.cloudinary.com/demo/image/upload/v1/entrega.jpg");
        verify(cloudinaryService).borrarSiCorresponde("https://res.cloudinary.com/demo/image/upload/v1/firma.jpg");
        verify(pedidoRepo).saveAll(List.of(p));
    }

    @Test
    void siNoHayCandidatosNoLlamaAlBorradoNiGuarda() {
        when(configuracionService.getInt(eq("retencion_imagenes_pedido_dias"), anyInt())).thenReturn(60);
        when(pedidoRepo.findConImagenesTerminadosAntesDe(any())).thenReturn(List.of());

        service.purgarMensajesViejos();

        verify(cloudinaryService, never()).borrarSiCorresponde(any());
        verify(pedidoRepo, never()).saveAll(any());
    }

    private Pedido pedidoTerminado() {
        Pedido p = new Pedido();
        EstadoPedido estado = new EstadoPedido();
        estado.setId("FINALIZADO");
        estado.setNombre("Finalizado");
        p.setEstado(estado);
        p.setFinalizadoEn(Instant.now());
        return p;
    }
}
