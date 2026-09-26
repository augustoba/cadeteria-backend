package com.cadeteria.backend.service;

import com.cadeteria.backend.common.BadRequestException;
import com.cadeteria.backend.config.AppProperties;
import com.cadeteria.backend.model.Incidencia;
import com.cadeteria.backend.model.Pedido;
import com.cadeteria.backend.repository.IncidenciaRepository;
import com.cadeteria.backend.repository.PedidoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Seguimiento automático del reclamo por problema con la entrega (2026-09-26). */
class ReclamoServiceTest {

    private PedidoRepository pedidoRepo;
    private IncidenciaRepository incidenciaRepo;
    private PedidoService pedidoService;
    private ConfiguracionService config;
    private WhatsappGatewayService whatsapp;
    private SmsGatewayService sms;
    private ReclamoService service;
    private Pedido pedido;
    private Incidencia incidente;

    @BeforeEach
    void setUp() {
        pedidoRepo = mock(PedidoRepository.class);
        incidenciaRepo = mock(IncidenciaRepository.class);
        pedidoService = mock(PedidoService.class);
        config = mock(ConfiguracionService.class);
        whatsapp = mock(WhatsappGatewayService.class);
        sms = mock(SmsGatewayService.class);
        AppProperties props = new AppProperties();
        service = new ReclamoService(pedidoRepo, incidenciaRepo, pedidoService, config, whatsapp, sms,
                mock(WebSocketPublisher.class), props);
        when(config.getInt(eq(ReclamoService.CONFIG_MIN_SEGUIMIENTO), anyInt())).thenReturn(10);
        when(config.getInt(eq(ReclamoService.CONFIG_MIN_CIERRE), anyInt())).thenReturn(10);
        when(config.getString(eq("nombre_cadeteria"), anyString())).thenReturn("Cadem");

        pedido = new Pedido();
        pedido.setId("p1");
        pedido.setNumero(1234L);
        pedido.setTokenSeguimiento("tok");
        pedido.setClienteTelefono("3815550000");
        pedido.setReclamoTipo("PROBLEMA_ENTREGA");
        pedido.setReclamoEstado("ABIERTO");
        pedido.setReclamoDetalle("El cliente reclama problemas en la entrega del pedido N° 1234: \"Llegó abierto\".");
        when(pedidoRepo.findById("p1")).thenReturn(Optional.of(pedido));
        when(pedidoService.getPorToken("tok")).thenReturn(pedido);

        incidente = new Incidencia();
        incidente.setId("i1");
        incidente.setPedidoId("p1");
        incidente.setEstado("ABIERTA");
        incidente.setOrigen(PedidoService.ORIGEN_RECLAMO);
        when(incidenciaRepo.findByEstadoAndOrigen("ABIERTA", PedidoService.ORIGEN_RECLAMO)).thenReturn(List.of(incidente));
        when(incidenciaRepo.findByPedidoIdAndOrigenAndEstado("p1", PedidoService.ORIGEN_RECLAMO, "ABIERTA")).thenReturn(List.of(incidente));
    }

    @Test
    void antesDeLosDiezMinutosNoLeEscribeAlCliente() {
        incidente.setCreadaEn(Instant.now().minus(Duration.ofMinutes(5)));
        service.seguimientoAutomatico();
        verify(whatsapp, never()).enviarYa(any(), any());
        assertNull(incidente.getSeguimientoEnviadoEn());
    }

    @Test
    void pasadosLosDiezMinutosLeEscribePorWhatsappConElDetalleYElLink() {
        incidente.setCreadaEn(Instant.now().minus(Duration.ofMinutes(11)));
        when(whatsapp.enviarYa(anyString(), anyString())).thenReturn(true);

        service.seguimientoAutomatico();

        verify(whatsapp).enviarYa(eq("3815550000"), contains("\"Llegó abierto\""));
        verify(whatsapp).enviarYa(eq("3815550000"), contains("/seguimiento/tok"));
        verify(sms, never()).enviar(any(), any(), any());
        assertNotNull(incidente.getSeguimientoEnviadoEn());
    }

    @Test
    void sinGatewayDeWhatsappVaPorSms() {
        incidente.setCreadaEn(Instant.now().minus(Duration.ofMinutes(11)));
        when(whatsapp.enviarYa(anyString(), anyString())).thenReturn(false);
        when(sms.isHabilitado()).thenReturn(true);

        service.seguimientoAutomatico();

        verify(sms).enviar(eq("p1"), eq("3815550000"), contains("se cerrará en 10 minutos"));
    }

    @Test
    void sinRespuestaDiezMinutosDespuesDelMensajeSeCierraSolo() {
        incidente.setCreadaEn(Instant.now().minus(Duration.ofMinutes(25)));
        incidente.setSeguimientoEnviadoEn(Instant.now().minus(Duration.ofMinutes(11)));

        service.seguimientoAutomatico();

        assertEquals("CERRADA", incidente.getEstado());
        assertEquals("Cerrado sin respuesta del cliente", incidente.getMotivoCierre());
        assertEquals("CERRADO", pedido.getReclamoEstado());
    }

    @Test
    void siElClientePidioContactoNoSeCierraSolo() {
        incidente.setCreadaEn(Instant.now().minus(Duration.ofMinutes(60)));
        incidente.setSeguimientoEnviadoEn(Instant.now().minus(Duration.ofMinutes(40)));
        pedido.setReclamoEstado("CONTACTO");

        service.seguimientoAutomatico();

        assertEquals("ABIERTA", incidente.getEstado());
    }

    @Test
    void elClienteDiceQueSeSolucionoYSeCierraAlInstante() {
        service.clienteSolucionado("tok");

        assertEquals("CERRADA", incidente.getEstado());
        assertEquals("Solucionado según el cliente", incidente.getMotivoCierre());
        assertEquals("CERRADO", pedido.getReclamoEstado());
    }

    @Test
    void sigueElProblemaQuedaEsperandoContactoYDevuelveElWhatsapp() {
        when(config.getString(eq(ReclamoService.CONFIG_WHATSAPP_ATENCION), anyString())).thenReturn("3814000000");

        assertEquals("3814000000", service.clienteSigueElProblema("tok"));
        assertEquals("CONTACTO", pedido.getReclamoEstado());
        assertEquals("ABIERTA", incidente.getEstado());
    }

    @Test
    void sinReclamoAbiertoLosBotonesNoHacenNada() {
        pedido.setReclamoEstado("CERRADO");
        assertThrows(BadRequestException.class, () -> service.clienteSolucionado("tok"));
    }
}
