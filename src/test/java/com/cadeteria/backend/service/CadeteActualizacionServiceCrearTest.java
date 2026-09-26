package com.cadeteria.backend.service;

import com.cadeteria.backend.common.BadRequestException;
import com.cadeteria.backend.common.ConflictException;
import com.cadeteria.backend.dto.CadeteActualizacionDtos.ActualizacionCadeteRequest;
import com.cadeteria.backend.model.Cadete;
import com.cadeteria.backend.model.CadeteActualizacion;
import com.cadeteria.backend.model.CadeteActualizacionCampo;
import com.cadeteria.backend.repository.CadeteActualizacionCampoRepository;
import com.cadeteria.backend.repository.CadeteActualizacionRepository;
import com.cadeteria.backend.repository.CadeteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CadeteActualizacionServiceCrearTest {

    private CadeteActualizacionRepository loteRepo;
    private CadeteActualizacionCampoRepository campoRepo;
    private CadeteRepository cadeteRepo;
    private CadeteActualizacionService service;

    @BeforeEach
    void setUp() {
        loteRepo = mock(CadeteActualizacionRepository.class);
        campoRepo = mock(CadeteActualizacionCampoRepository.class);
        cadeteRepo = mock(CadeteRepository.class);
        service = new CadeteActualizacionService(loteRepo, campoRepo, cadeteRepo);

        when(loteRepo.save(any(CadeteActualizacion.class))).thenAnswer(i -> i.getArgument(0));
        when(campoRepo.save(any(CadeteActualizacionCampo.class))).thenAnswer(i -> i.getArgument(0));
    }

    private Cadete cadeteActual() {
        Cadete c = new Cadete();
        c.setId("c1");
        c.setUsername("jperez");
        c.setFotoVehiculoUrl("http://viejo/vehiculo.jpg");
        c.setVehiculoColor("Rojo");
        c.setVehiculoAnio(2020);
        when(cadeteRepo.findByUsername("jperez")).thenReturn(Optional.of(c));
        return c;
    }

    @Test
    void ignoraCamposIgualesAlValorActual() {
        cadeteActual();
        when(campoRepo.existePendientePara("c1", "PENDIENTE")).thenReturn(false);

        service.crear("jperez", new ActualizacionCadeteRequest(
                null, null, null, null, null, null, "Rojo", null, 2021));

        ArgumentCaptor<CadeteActualizacionCampo> captor = ArgumentCaptor.forClass(CadeteActualizacionCampo.class);
        verify(campoRepo, times(1)).save(captor.capture());
        CadeteActualizacionCampo guardado = captor.getValue();

        assertEquals("VEHICULO_ANIO", guardado.getCampo());
        assertEquals("2020", guardado.getValorAnterior());
        assertEquals("2021", guardado.getValorPropuesto());
        assertEquals("PENDIENTE", guardado.getEstado());
    }

    @Test
    void fallaSiNoHayNingunCambioReal() {
        cadeteActual();
        when(campoRepo.existePendientePara("c1", "PENDIENTE")).thenReturn(false);

        assertThrows(BadRequestException.class, () ->
                service.crear("jperez", new ActualizacionCadeteRequest(
                        null, null, null, null, null, null, "Rojo", null, null)));
    }

    @Test
    void fallaSiYaTieneUnCampoPendiente() {
        cadeteActual();
        when(campoRepo.existePendientePara("c1", "PENDIENTE")).thenReturn(true);

        assertThrows(ConflictException.class, () ->
                service.crear("jperez", new ActualizacionCadeteRequest(
                        null, null, null, null, null, null, "Azul", null, null)));
    }

    @Test
    void creaUnCampoPorCadaCambioConElValorAnteriorCorrecto() {
        cadeteActual();
        when(campoRepo.existePendientePara("c1", "PENDIENTE")).thenReturn(false);

        service.crear("jperez", new ActualizacionCadeteRequest(
                null, "http://nuevo/vehiculo.jpg", null, null, null, null, "Azul", null, 2022));

        ArgumentCaptor<CadeteActualizacionCampo> captor = ArgumentCaptor.forClass(CadeteActualizacionCampo.class);
        verify(campoRepo, times(3)).save(captor.capture());
        List<CadeteActualizacionCampo> guardados = captor.getAllValues();

        CadeteActualizacionCampo color = guardados.stream().filter(c -> "VEHICULO_COLOR".equals(c.getCampo())).findFirst().orElseThrow();
        assertEquals("Rojo", color.getValorAnterior());
        assertEquals("Azul", color.getValorPropuesto());
        assertEquals("PENDIENTE", color.getEstado());

        CadeteActualizacionCampo anio = guardados.stream().filter(c -> "VEHICULO_ANIO".equals(c.getCampo())).findFirst().orElseThrow();
        assertEquals("2020", anio.getValorAnterior());
        assertEquals("2022", anio.getValorPropuesto());

        CadeteActualizacionCampo vehiculo = guardados.stream().filter(c -> "FOTO_VEHICULO".equals(c.getCampo())).findFirst().orElseThrow();
        assertEquals("http://viejo/vehiculo.jpg", vehiculo.getValorAnterior());
        assertEquals("http://nuevo/vehiculo.jpg", vehiculo.getValorPropuesto());
    }
}
