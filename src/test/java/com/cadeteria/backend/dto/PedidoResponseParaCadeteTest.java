package com.cadeteria.backend.dto;

import com.cadeteria.backend.dto.PedidoDtos.PedidoResponse;
import com.cadeteria.backend.model.EstadoPedido;
import com.cadeteria.backend.model.Pedido;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * El cadete decide si acepta con los datos de siempre; el detalle del pedido y el piso/depto
 * y las observaciones de las direcciones le aparecen recién al aceptar (mejora 2026-09-24).
 * El panel (`from`) los ve siempre.
 */
class PedidoResponseParaCadeteTest {

    private Pedido pedido(String estadoId, Instant aceptadoEn) {
        EstadoPedido estado = new EstadoPedido();
        estado.setId(estadoId);
        Pedido p = new Pedido();
        p.setId("p1");
        p.setEstado(estado);
        p.setAceptadoEn(aceptadoEn);
        p.setPrecio(new BigDecimal("1000"));
        p.setOrigenDireccion("San Juan 354");
        p.setDestinoDireccion("Mendoza 800");
        p.setDetalle("Sobre con documentos");
        p.setOrigenPisoDepto("3° B");
        p.setOrigenObservaciones("Timbre roto, llamar");
        p.setDestinoPisoDepto("PB");
        p.setDestinoObservaciones("Porton verde");
        return p;
    }

    @Test
    void ofertaPendienteOcultaDetalleYExtrasDeDireccion() {
        PedidoResponse r = PedidoResponse.paraCadete(pedido("PENDIENTE", null));
        assertNull(r.detalle());
        assertNull(r.origenPisoDepto());
        assertNull(r.origenObservaciones());
        assertNull(r.destinoPisoDepto());
        assertNull(r.destinoObservaciones());
        assertEquals("San Juan 354", r.origenDireccion());
    }

    @Test
    void pendienteConAceptadoEnDeOtroCadeteIgualOculta() {
        // "Quitar" un pedido EN_CURSO no limpia aceptadoEn: el siguiente cadete no tiene que verlo.
        PedidoResponse r = PedidoResponse.paraCadete(pedido("PENDIENTE", Instant.now()));
        assertNull(r.detalle());
        assertNull(r.origenPisoDepto());
    }

    @Test
    void enCursoMuestraTodo() {
        PedidoResponse r = PedidoResponse.paraCadete(pedido("EN_CURSO", Instant.now()));
        assertEquals("Sobre con documentos", r.detalle());
        assertEquals("3° B", r.origenPisoDepto());
        assertEquals("Timbre roto, llamar", r.origenObservaciones());
        assertEquals("PB", r.destinoPisoDepto());
        assertEquals("Porton verde", r.destinoObservaciones());
    }

    @Test
    void elPanelLoVeSiempre() {
        PedidoResponse r = PedidoResponse.from(pedido("PENDIENTE", null));
        assertEquals("Sobre con documentos", r.detalle());
        assertEquals("3° B", r.origenPisoDepto());
    }
}
