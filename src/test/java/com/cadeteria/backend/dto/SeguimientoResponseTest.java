package com.cadeteria.backend.dto;

import com.cadeteria.backend.dto.PublicoDtos.SeguimientoResponse;
import com.cadeteria.backend.model.EstadoPedido;
import com.cadeteria.backend.model.Pedido;
import com.cadeteria.backend.model.PedidoParada;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Seguimiento del cliente (2026-11-11): reclamaban que no se veía el piso ni el depto. Piso y depto
 * van siempre; las observaciones solo si las escribió el cliente (pedido cargado desde la web), porque
 * en los pedidos del panel las escribe el operador y pueden ser internas.
 */
class SeguimientoResponseTest {

    @Test
    void mandaPisoYDeptoDeOrigenYDestino() {
        Pedido p = pedido("PANEL");
        p.setOrigenPiso("2");
        p.setOrigenDepto("A");
        p.setDestinoPiso("5");
        p.setDestinoDepto("C");

        SeguimientoResponse r = SeguimientoResponse.from(p);

        assertThat(r.origenPiso()).isEqualTo("2");
        assertThat(r.origenDepto()).isEqualTo("A");
        assertThat(r.destinoPiso()).isEqualTo("5");
        assertThat(r.destinoDepto()).isEqualTo("C");
    }

    @Test
    void lasObservacionesDelClienteSeMuestran() {
        Pedido p = pedido("WEB");
        p.setOrigenObservaciones("Portón verde");
        p.setDestinoObservaciones("Timbre no anda, llamar");

        SeguimientoResponse r = SeguimientoResponse.from(p);

        assertThat(r.origenObservaciones()).isEqualTo("Portón verde");
        assertThat(r.destinoObservaciones()).isEqualTo("Timbre no anda, llamar");
    }

    @Test
    void lasObservacionesDelOperadorNoSeMuestran() {
        for (String origenCarga : new String[] {"PANEL", null}) {
            Pedido p = pedido(origenCarga);
            p.setOrigenObservaciones("Cliente complicado");
            p.setDestinoObservaciones("Cobrar antes");

            SeguimientoResponse r = SeguimientoResponse.from(p);

            assertThat(r.origenObservaciones()).isNull();
            assertThat(r.destinoObservaciones()).isNull();
        }
    }

    @Test
    void mandaLasParadasEnOrdenConSiYaSeEntregaron() {
        Pedido p = pedido("PANEL");
        p.setParadas(List.of(parada(p, 2, "Mitre 50", null), parada(p, 1, "Lavalle 300", Instant.now())));

        SeguimientoResponse r = SeguimientoResponse.from(p);

        assertThat(r.paradas()).extracting(PublicoDtos.ParadaSeguimiento::direccion).containsExactly("Lavalle 300", "Mitre 50");
        assertThat(r.paradas()).extracting(PublicoDtos.ParadaSeguimiento::entregada).containsExactly(true, false);
    }

    private static Pedido pedido(String origenCarga) {
        EstadoPedido estado = new EstadoPedido();
        estado.setId("EN_CURSO");
        estado.setNombre("En curso");
        Pedido p = new Pedido();
        p.setEstado(estado);
        p.setOrigenDireccion("Laprida 150");
        p.setDestinoDireccion("Av. Belgrano 2500");
        p.setPrecio(new BigDecimal("1000"));
        p.setOrigenCarga(origenCarga);
        return p;
    }

    private static PedidoParada parada(Pedido p, int orden, String direccion, Instant entregadoEn) {
        PedidoParada pa = new PedidoParada();
        pa.setPedido(p);
        pa.setOrden(orden);
        pa.setDireccion(direccion);
        pa.setEntregadoEn(entregadoEn);
        return pa;
    }
}
