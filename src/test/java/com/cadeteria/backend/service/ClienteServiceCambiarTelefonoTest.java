package com.cadeteria.backend.service;

import com.cadeteria.backend.common.BadRequestException;
import com.cadeteria.backend.common.ConflictException;
import com.cadeteria.backend.model.Cliente;
import com.cadeteria.backend.model.ClienteTelefonoCambio;
import com.cadeteria.backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Cambiar el teléfono desde la ficha (2026-10-24): se muda todo el cliente al número nuevo. */
class ClienteServiceCambiarTelefonoTest {

    private ClienteService service;
    private ClienteRepository repo;
    private PedidoRepository pedidoRepo;
    private ReporteClienteRepository reporteRepo;
    private DireccionClienteOcultaRepository ocultaRepo;
    private ClienteTelefonoCambioRepository cambioRepo;

    @BeforeEach
    void setUp() {
        repo = mock(ClienteRepository.class);
        pedidoRepo = mock(PedidoRepository.class);
        reporteRepo = mock(ReporteClienteRepository.class);
        ocultaRepo = mock(DireccionClienteOcultaRepository.class);
        cambioRepo = mock(ClienteTelefonoCambioRepository.class);
        service = new ClienteService(repo, pedidoRepo, reporteRepo, ocultaRepo, cambioRepo);
        when(repo.save(any(Cliente.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void mudaLaFichaLosPedidosReportesYDirecciones() {
        Cliente viejo = new Cliente();
        viejo.setTelefono("3794111111");
        viejo.setEmpresa("Farmacia");
        viejo.setTarifaEspecial(new BigDecimal("900"));
        viejo.setProblematico(true);
        when(repo.findById("3794111111")).thenReturn(Optional.of(viejo));
        when(pedidoRepo.cambiarTelefonoCliente("3794111111", "3794222222")).thenReturn(7);

        var r = service.cambiarTelefono("3794111111", "3794 22-2222", "operador");

        assertEquals("3794222222", r.telefono());
        ArgumentCaptor<Cliente> nuevo = ArgumentCaptor.forClass(Cliente.class);
        verify(repo).save(nuevo.capture());
        assertEquals("3794222222", nuevo.getValue().getTelefono());
        assertEquals("Farmacia", nuevo.getValue().getEmpresa());
        assertEquals(new BigDecimal("900"), nuevo.getValue().getTarifaEspecial());
        assertTrue(nuevo.getValue().isProblematico());
        verify(repo).delete(viejo);
        verify(reporteRepo).cambiarTelefono("3794111111", "3794222222");
        verify(ocultaRepo).cambiarTelefono("3794111111", "3794222222");
        ArgumentCaptor<ClienteTelefonoCambio> cambio = ArgumentCaptor.forClass(ClienteTelefonoCambio.class);
        verify(cambioRepo).save(cambio.capture());
        assertEquals("operador", cambio.getValue().getCambiadoPorUsername());
        assertEquals(7, cambio.getValue().getPedidosMovidos());
    }

    @Test
    void sinFichaGuardadaMudaIgualLosPedidos() {
        when(repo.findById("3794111111")).thenReturn(Optional.empty());
        when(pedidoRepo.existsByClienteTelefono("3794111111")).thenReturn(true);

        service.cambiarTelefono("3794111111", "3794222222", "operador");

        verify(repo, never()).save(any());
        verify(pedidoRepo).cambiarTelefonoCliente("3794111111", "3794222222");
    }

    @Test
    void siElNumeroNuevoYaEsDeOtroClienteNoLosJunta() {
        when(repo.findById("3794111111")).thenReturn(Optional.of(new Cliente()));
        when(pedidoRepo.existsByClienteTelefono("3794222222")).thenReturn(true);

        assertThrows(ConflictException.class, () -> service.cambiarTelefono("3794111111", "3794222222", "operador"));
        verify(pedidoRepo, never()).cambiarTelefonoCliente(any(), any());
    }

    @Test
    void unTelefonoInvalidoNoSeAcepta() {
        assertThrows(BadRequestException.class, () -> service.cambiarTelefono("3794111111", "abc", "operador"));
    }
}
