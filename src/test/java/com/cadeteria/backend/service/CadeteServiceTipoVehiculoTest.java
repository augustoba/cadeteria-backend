package com.cadeteria.backend.service;

import com.cadeteria.backend.dto.CadeteDtos.CadeteRequest;
import com.cadeteria.backend.dto.PublicoDtos.CadeteInfo;
import com.cadeteria.backend.model.Cadete;
import com.cadeteria.backend.model.EstadoCadete;
import com.cadeteria.backend.model.TipoVehiculo;
import com.cadeteria.backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Cadetes que alternan moto y bici por semana (2026-11-20): los datos de la moto quedan guardados. */
class CadeteServiceTipoVehiculoTest {

    private CadeteService service;
    private CadeteRepository repo;
    private PedidoRepository pedidoRepo;
    private FcmService fcm;
    private Cadete cadete;

    private static TipoVehiculo tipo(String id, String nombre) {
        TipoVehiculo t = new TipoVehiculo();
        t.setId(id);
        t.setNombre(nombre);
        return t;
    }

    @BeforeEach
    void setUp() {
        repo = mock(CadeteRepository.class);
        TipoVehiculoRepository tipoRepo = mock(TipoVehiculoRepository.class);
        pedidoRepo = mock(PedidoRepository.class);
        fcm = mock(FcmService.class);
        service = new CadeteService(repo, tipoRepo, mock(EstadoCadeteRepository.class),
                mock(PasswordEncoder.class), mock(GeocodingService.class), mock(CadeteSesionService.class),
                mock(WebSocketPublisher.class), fcm, pedidoRepo,
                mock(PedidoUbicacionRepository.class), mock(AvisoGeneralRepository.class),
                mock(AvisoGeneralLecturaRepository.class), mock(ConfiguracionService.class),
                mock(CadeteEstadoLogRepository.class), mock(MovimientoCreditoRepository.class), mock(MetricasService.class),
                mock(IncidenciaService.class), mock(PagoSemanalMovimientoRepository.class));

        when(tipoRepo.findById("MOTO")).thenReturn(Optional.of(tipo("MOTO", "Moto")));
        when(tipoRepo.findById("BICI")).thenReturn(Optional.of(tipo("BICI", "Bici")));

        cadete = new Cadete();
        cadete.setId("c1");
        cadete.setFcmToken("fcm");
        EstadoCadete libre = new EstadoCadete();
        libre.setId("LIBRE");
        cadete.setEstado(libre);
        cadete.setTipoVehiculo(tipo("MOTO", "Moto"));
        cadete.setVehiculoPatente("A123BCD");
        cadete.setVehiculoColor("Rojo");
        cadete.setVehiculoMarca("Honda");
        cadete.setVehiculoModelo("Wave");
        cadete.setVehiculoAnio(2020);
        cadete.setFotoVehiculoUrl("/fotos/moto.jpg");
        cadete.setFotoTarjetaVerdeUrl("/fotos/tv.jpg");
        cadete.setFotoTarjetaVerdeDorsoUrl("/fotos/tv-dorso.jpg");
        when(repo.findById("c1")).thenReturn(Optional.of(cadete));
        when(repo.findByDni(any())).thenReturn(Optional.empty());
        when(repo.findByUsername(any())).thenReturn(Optional.empty());
        when(repo.save(any(Cadete.class))).thenAnswer(i -> i.getArgument(0));
    }

    private void assertDatosDeLaMotoGuardados() {
        assertEquals("A123BCD", cadete.getVehiculoPatente());
        assertEquals("Rojo", cadete.getVehiculoColor());
        assertEquals("Honda", cadete.getVehiculoMarca());
        assertEquals("Wave", cadete.getVehiculoModelo());
        assertEquals(2020, cadete.getVehiculoAnio());
        assertEquals("/fotos/moto.jpg", cadete.getFotoVehiculoUrl());
        assertEquals("/fotos/tv.jpg", cadete.getFotoTarjetaVerdeUrl());
        assertEquals("/fotos/tv-dorso.jpg", cadete.getFotoTarjetaVerdeDorsoUrl());
    }

    @Test
    void pasarABiciConElBotonGuardaLosDatosDeLaMotoYLeAvisa() {
        service.cambiarTipoVehiculo("c1", "BICI");

        assertEquals("BICI", cadete.getTipoVehiculo().getId());
        assertDatosDeLaMotoGuardados();
        verify(fcm).enviar(eq("fcm"), eq("Cambiamos tu vehículo"), eq("Desde ahora figurás en bici."), any());
    }

    @Test
    void volverAMotoRecuperaLosDatosQueTenia() {
        service.cambiarTipoVehiculo("c1", "BICI");
        service.cambiarTipoVehiculo("c1", "MOTO");

        assertEquals("MOTO", cadete.getTipoVehiculo().getId());
        assertDatosDeLaMotoGuardados();
    }

    @Test
    void conViajesActivosIgualSeCambia() {
        when(pedidoRepo.countByCadeteAsignadoIdAndEstadoIdIn(eq("c1"), any())).thenReturn(2L);

        service.cambiarTipoVehiculo("c1", "BICI");

        assertEquals("BICI", cadete.getTipoVehiculo().getId());
    }

    @Test
    void siYaTieneEseVehiculoNoGuardaNiAvisa() {
        service.cambiarTipoVehiculo("c1", "MOTO");

        verify(repo, never()).save(any());
        verify(fcm, never()).enviar(any(), any(), any(), any());
    }

    @Test
    void guardarElFormularioEnBiciNoBorraLosDatosDeLaMoto() {
        // El formulario del panel manda vacíos los campos de la moto cuando está en BICI.
        service.update("c1", request("BICI", null, null));

        assertEquals("BICI", cadete.getTipoVehiculo().getId());
        assertDatosDeLaMotoGuardados();
    }

    @Test
    void guardarElFormularioEnMotoActualizaLosDatosDeLaMoto() {
        service.update("c1", request("MOTO", "AB123CD", "Azul"));

        assertEquals("AB123CD", cadete.getVehiculoPatente());
        assertEquals("Azul", cadete.getVehiculoColor());
    }

    @Test
    void enBiciElQueRecibeElPedidoNoVeLosDatosDeLaMoto() {
        cadete.setTipoVehiculo(tipo("BICI", "Bici"));

        CadeteInfo info = CadeteInfo.from(cadete);

        assertEquals("Bici", info.tipoVehiculo());
        assertNull(info.vehiculoPatente());
        assertNull(info.vehiculoColor());
        assertNull(info.fotoVehiculoUrl());
    }

    @Test
    void enMotoElQueRecibeElPedidoVeLaPatenteYLaFoto() {
        CadeteInfo info = CadeteInfo.from(cadete);

        assertEquals("A123BCD", info.vehiculoPatente());
        assertEquals("Rojo", info.vehiculoColor());
        assertEquals("/fotos/moto.jpg", info.fotoVehiculoUrl());
    }

    /** Lo que manda el formulario: en BICI van vacíos todos los campos de la moto. */
    private CadeteRequest request(String tipo, String patente, String color) {
        boolean moto = "MOTO".equals(tipo);
        return new CadeteRequest("Juan", "Perez", "30111222", "3511234567", null, null, tipo,
                color, patente, moto ? "Honda" : null, moto ? "Wave" : null, moto ? 2020 : null,
                moto ? "/fotos/moto.jpg" : null, null, null,
                moto ? "/fotos/tv.jpg" : null, moto ? "/fotos/tv-dorso.jpg" : null,
                "30111222", null, BigDecimal.ZERO, 1, null, null, null, null, "SEMANAL", null, null, null, null);
    }
}
