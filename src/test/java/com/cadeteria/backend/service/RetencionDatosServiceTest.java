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
    private ChatMensajeRepository chatRepo;
    private RetencionDatosService service;

    @BeforeEach
    void setUp() {
        pedidoRepo = mock(PedidoRepository.class);
        configuracionService = mock(ConfiguracionService.class);
        cloudinaryService = mock(CloudinaryService.class);
        chatRepo = mock(ChatMensajeRepository.class);
        service = new RetencionDatosService(mock(WhatsappMensajeRepository.class), chatRepo,
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

    @Test
    void lasFotosYAudiosViejosDelChatSeBorranDeCloudinaryPeroElMensajeQueda() {
        // 2026-09-29: los archivos del chat son lo que llena el plan gratis de Cloudinary; el texto no pesa.
        when(configuracionService.getInt(eq(RetencionDatosService.CLAVE_ARCHIVOS_CHAT_DIAS), anyInt())).thenReturn(30);
        com.cadeteria.backend.model.ChatMensaje foto = new com.cadeteria.backend.model.ChatMensaje();
        foto.setImagenUrl("https://res.cloudinary.com/demo/image/upload/v1/chat.jpg");
        com.cadeteria.backend.model.ChatMensaje audio = new com.cadeteria.backend.model.ChatMensaje();
        audio.setAudioUrl("https://res.cloudinary.com/demo/video/upload/v1/nota.m4a");
        when(chatRepo.findConArchivosEnviadosAntesDe(any())).thenReturn(List.of(foto, audio));

        service.purgarMensajesViejos();

        verify(cloudinaryService).borrarSiCorresponde("https://res.cloudinary.com/demo/image/upload/v1/chat.jpg");
        verify(cloudinaryService).borrarSiCorresponde("https://res.cloudinary.com/demo/video/upload/v1/nota.m4a");
        assertNull(foto.getImagenUrl());
        assertNull(audio.getAudioUrl());
        org.junit.jupiter.api.Assertions.assertTrue(foto.getTexto().contains("Foto borrada"));
        org.junit.jupiter.api.Assertions.assertTrue(audio.getTexto().contains("Audio borrado"));
        verify(chatRepo).saveAll(List.of(foto, audio));
        verify(chatRepo, never()).deleteAll(any());
    }

    @Test
    void alBorrarMensajesViejosDelChatTambienBorraSusArchivos() {
        when(configuracionService.getInt(eq("retencion_chat_dias"), anyInt())).thenReturn(90);
        com.cadeteria.backend.model.ChatMensaje foto = new com.cadeteria.backend.model.ChatMensaje();
        foto.setImagenUrl("https://res.cloudinary.com/demo/image/upload/v1/vieja.jpg");
        when(chatRepo.findByEnviadoEnBefore(any())).thenReturn(List.of(foto));

        service.purgarMensajesViejos();

        verify(cloudinaryService).borrarSiCorresponde("https://res.cloudinary.com/demo/image/upload/v1/vieja.jpg");
        verify(chatRepo).deleteAll(List.of(foto));
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
