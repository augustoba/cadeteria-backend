package com.cadeteria.backend.service;

import com.cadeteria.backend.model.Pedido;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * El push "Nuevo viaje" lleva la vuelta al origen y el dinero a disponer (2026-10-14): le llegan
 * al cadete antes de aceptar aunque tenga una APK que no los muestra.
 */
class PedidoServiceTextoViajeNuevoTest {

    @Test
    void sinNadaEspecial() {
        assertEquals("Tenes un viaje nuevo asignado", PedidoService.textoViajeNuevo(new Pedido()));
    }

    @Test
    void conVueltaYDineroADisponer() {
        Pedido p = new Pedido();
        p.setRetornaAlOrigen(true);
        p.setMontoDisponer(new BigDecimal("15000.00"));
        assertEquals("Tenes un viaje nuevo asignado, con vuelta al origen, debes disponer de $15000",
                PedidoService.textoViajeNuevo(p));
    }
}
