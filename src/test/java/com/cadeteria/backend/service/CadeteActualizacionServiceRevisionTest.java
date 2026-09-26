package com.cadeteria.backend.service;

import com.cadeteria.backend.common.BadRequestException;
import com.cadeteria.backend.common.ConflictException;
import com.cadeteria.backend.model.Cadete;
import com.cadeteria.backend.model.CadeteActualizacion;
import com.cadeteria.backend.model.CadeteActualizacionCampo;
import com.cadeteria.backend.repository.CadeteActualizacionCampoRepository;
import com.cadeteria.backend.repository.CadeteActualizacionRepository;
import com.cadeteria.backend.repository.CadeteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CadeteActualizacionServiceRevisionTest {

    private CadeteActualizacionCampoRepository campoRepo;
    private CadeteRepository cadeteRepo;
    private CadeteActualizacionService service;

    @BeforeEach
    void setUp() {
        campoRepo = mock(CadeteActualizacionCampoRepository.class);
        cadeteRepo = mock(CadeteRepository.class);
        service = new CadeteActualizacionService(mock(CadeteActualizacionRepository.class), campoRepo, cadeteRepo);

        when(campoRepo.save(any(CadeteActualizacionCampo.class))).thenAnswer(i -> i.getArgument(0));
        when(cadeteRepo.save(any(Cadete.class))).thenAnswer(i -> i.getArgument(0));
    }

    private CadeteActualizacionCampo campoPendiente(String campo, String valorPropuesto) {
        Cadete cadete = new Cadete();
        cadete.setId("c1");
        cadete.setVehiculoColor("Rojo");

        CadeteActualizacion lote = new CadeteActualizacion();
        lote.setId("lote1");
        lote.setCadete(cadete);

        CadeteActualizacionCampo c = new CadeteActualizacionCampo();
        c.setId("campo1");
        c.setActualizacion(lote);
        c.setCampo(campo);
        c.setValorAnterior("Rojo");
        c.setValorPropuesto(valorPropuesto);
        c.setEstado("PENDIENTE");
        when(campoRepo.findById("campo1")).thenReturn(Optional.of(c));
        return c;
    }

    @Test
    void aprobarAplicaElValorAlCadeteYMarcaAprobado() {
        CadeteActualizacionCampo campo = campoPendiente("VEHICULO_COLOR", "Azul");

        service.aprobarCampo("campo1", "admin");

        assertEquals("APROBADO", campo.getEstado());
        assertEquals("admin", campo.getResueltoPorUsername());
        assertNotNull(campo.getResueltoEn());
        assertEquals("Azul", campo.getActualizacion().getCadete().getVehiculoColor());
    }

    @Test
    void aprobarUnCampoYaResueltoFalla() {
        CadeteActualizacionCampo campo = campoPendiente("VEHICULO_COLOR", "Azul");
        campo.setEstado("APROBADO");

        assertThrows(ConflictException.class, () -> service.aprobarCampo("campo1", "admin"));
    }

    @Test
    void rechazarNoTocaElCadeteYGuardaElMotivo() {
        CadeteActualizacionCampo campo = campoPendiente("VEHICULO_COLOR", "Azul");

        service.rechazarCampo("campo1", "no coincide con la foto del vehículo", "admin");

        assertEquals("RECHAZADO", campo.getEstado());
        assertEquals("no coincide con la foto del vehículo", campo.getMotivoRechazo());
        assertEquals("Rojo", campo.getActualizacion().getCadete().getVehiculoColor(), "rechazar no debe tocar el Cadete real");
        verify(cadeteRepo, never()).save(any());
    }

    @Test
    void aprobarUnCampoDeAnioParseaElEnteroCorrectamente() {
        CadeteActualizacionCampo campo = campoPendiente("VEHICULO_ANIO", "2022");

        service.aprobarCampo("campo1", "admin");

        assertEquals(2022, campo.getActualizacion().getCadete().getVehiculoAnio());
    }

    @Test
    void listarPendientesDelegaEnElRepositorioConElEstadoPendiente() {
        service.listarPendientes();

        verify(campoRepo).findByEstadoOrderByActualizacionCreadoEnDesc("PENDIENTE");
    }
}
