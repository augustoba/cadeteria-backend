package com.cadeteria.backend.service;

import com.cadeteria.backend.dto.EnElLugarDtos.RegistroPedido;
import com.cadeteria.backend.dto.EnElLugarDtos.ResumenCadete;
import com.cadeteria.backend.model.Cadete;
import com.cadeteria.backend.model.Pedido;
import com.cadeteria.backend.model.PedidoParada;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Registros de "en el lugar" para la ficha del cadete y Métricas (carril B, 2026-09-28). */
class EnElLugarServiceTest {

    @Test
    void cadaRetiroParadaOEntregaFueraDeZonaCuentaUnaVez() {
        Cadete c = new Cadete();
        c.setId("c1");
        c.setNombre("Juan");
        c.setApellido("Pérez");
        c.setIntentosUbicacionSimulada(2);

        Pedido p1 = new Pedido();
        p1.setCadeteAsignado(c);
        p1.setRetiroFueraZona(true);
        p1.setEntregaFueraZona(true);
        PedidoParada parada = new PedidoParada();
        parada.setFueraZona(true);
        p1.getParadas().add(parada);

        Pedido p2 = new Pedido();
        p2.setCadeteAsignado(c);
        p2.setFinalizadoPorAdmin("admin");
        p2.setUbicacionImprecisa(true);

        List<RegistroPedido> registros = List.of(EnElLugarService.registro(p1), EnElLugarService.registro(p2));
        assertEquals(List.of("RETIRO_FUERA_ZONA", "PARADA_FUERA_ZONA", "ENTREGA_FUERA_ZONA"), registros.get(0).tipos());

        ResumenCadete r = EnElLugarService.resumen(c, registros);
        assertEquals("Juan Pérez", r.cadeteNombre());
        assertEquals(3, r.vecesFueraZona());
        assertEquals(1, r.vecesImprecisa());
        assertEquals(1, r.vecesFinalizadoPorAdmin());
        assertEquals(2, r.intentosUbicacionSimulada());
    }
}
