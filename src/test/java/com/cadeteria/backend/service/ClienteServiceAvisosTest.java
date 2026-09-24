package com.cadeteria.backend.service;

import com.cadeteria.backend.dto.ClienteDtos.ClienteAvisoResponse;
import com.cadeteria.backend.model.Cliente;
import com.cadeteria.backend.model.ReporteCliente;
import com.cadeteria.backend.repository.ClienteRepository;
import com.cadeteria.backend.repository.PedidoRepository;
import com.cadeteria.backend.repository.ReporteClienteRepository;
import com.cadeteria.backend.util.TelefonoUtils;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Aviso del teléfono en la bandeja de solicitudes (spec-antiabuso Fases 1 y 4). */
class ClienteServiceAvisosTest {

    private ReporteCliente reporte(String tel, String tipo, Instant cuando) {
        ReporteCliente r = new ReporteCliente();
        r.setTelefono(tel);
        r.setTipo(tipo);
        r.setCreadoEn(cuando);
        return r;
    }

    @Test
    void agrupaReportesPorTipoYOmiteTelefonosSinNada() {
        ClienteRepository clienteRepo = mock(ClienteRepository.class);
        ReporteClienteRepository reporteRepo = mock(ReporteClienteRepository.class);
        ClienteService service = new ClienteService(clienteRepo, mock(PedidoRepository.class), reporteRepo);

        String conReportes = TelefonoUtils.normalizar("3815551122");
        String problematico = TelefonoUtils.normalizar("3815553344");
        String limpio = TelefonoUtils.normalizar("3815556677");

        Cliente c = new Cliente();
        c.setTelefono(problematico);
        c.setProblematico(true);
        c.setNotasProblematico("No pagó");
        when(clienteRepo.findAllById(anyIterable())).thenReturn(List.of(c));

        Instant ultimo = Instant.parse("2026-09-18T12:00:00Z");
        when(reporteRepo.findByTelefonoInOrderByCreadoEnDesc(anyCollection())).thenReturn(List.of(
                reporte(conReportes, "NO_DECLARO_VALORES", ultimo),
                reporte(conReportes, "DEMORO", Instant.parse("2026-09-10T12:00:00Z")),
                reporte(conReportes, "DEMORO", Instant.parse("2026-09-01T12:00:00Z"))));

        Map<String, ClienteAvisoResponse> avisos = service.avisos(List.of("3815551122", "3815553344", "3815556677"));

        ClienteAvisoResponse a = avisos.get(conReportes);
        assertEquals(3, a.cantidadReportes());
        assertEquals(2L, a.reportesPorTipo().get("DEMORO"));
        assertEquals(1L, a.reportesPorTipo().get("NO_DECLARO_VALORES"));
        assertEquals(ultimo, a.ultimoReporteEn());
        assertFalse(a.problematico());

        assertTrue(avisos.get(problematico).problematico());
        assertFalse(avisos.containsKey(limpio));
    }
}
