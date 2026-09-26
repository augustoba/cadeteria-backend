package com.cadeteria.backend.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * "Avisar al cliente" por WhatsApp Web (2026-09-26): el link de WhatsApp necesita el celular en
 * formato internacional, y en Argentina se carga de muchas formas (con 0, con 15, con +54...).
 */
class PedidoServiceAvisoWhatsappTest {

    @Test
    void normalizaLasFormasHabitualesDeCargarUnCelularDeTucuman() {
        assertEquals("5493815551234", PedidoService.telefonoParaWhatsapp("3815551234"));
        assertEquals("5493815551234", PedidoService.telefonoParaWhatsapp("381 15 555-1234"));
        assertEquals("5493815551234", PedidoService.telefonoParaWhatsapp("0381 155551234"));
        assertEquals("5493815551234", PedidoService.telefonoParaWhatsapp("+54 9 381 555-1234"));
        assertEquals("5493815551234", PedidoService.telefonoParaWhatsapp("54 381 5551234"));
    }

    @Test
    void sacaEl15TambienConAreaDeDosDigitos() {
        assertEquals("5491155551234", PedidoService.telefonoParaWhatsapp("11 15 5555-1234"));
    }
}
