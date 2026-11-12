package com.cadeteria.backend.repository;

import com.cadeteria.backend.model.Cadete;
import com.cadeteria.backend.model.EstadoCadete;
import com.cadeteria.backend.model.EstadoPedido;
import com.cadeteria.backend.model.Pedido;
import com.cadeteria.backend.model.TipoVehiculo;
import com.cadeteria.backend.service.BusquedaPedidos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pantalla "Pedidos" del panel (2026-11-11): la búsqueda corre en la base (H2 en modo MySQL, igual
 * que BackendApplicationTests), así que se prueba la consulta de verdad y no un mock.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:busqueda_pedidos;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.globally_quoted_identifiers=false"
})
class PedidoBusquedaRepositoryTest {

    private static final ZoneId ZONA = ZoneId.of("America/Argentina/Buenos_Aires");

    @Autowired
    private TestEntityManager em;
    @Autowired
    private PedidoRepository repo;

    private Cadete juan;
    private Cadete pedro;
    private long siguienteNumero = 1_500_000;

    @BeforeEach
    void datos() {
        for (String id : List.of("SIN_ASIGNAR", "PENDIENTE", "EN_CURSO", "FINALIZADO", "CANCELADO", "NO_ENTREGADO")) {
            EstadoPedido e = new EstadoPedido();
            e.setId(id);
            e.setNombre(id);
            em.persist(e);
        }
        TipoVehiculo moto = new TipoVehiculo();
        moto.setId("MOTO");
        moto.setNombre("Moto");
        em.persist(moto);
        EstadoCadete libre = new EstadoCadete();
        libre.setId("LIBRE");
        libre.setNombre("Libre");
        em.persist(libre);
        juan = cadete("Juan", "30111222", moto, libre);
        pedro = cadete("Pedro", "30333444", moto, libre);
    }

    @Test
    void sinFiltrosTraeTodoDelMasNuevoAlMasViejo() {
        Pedido viejo = pedido("SIN_ASIGNAR", null, "3814111111", local(2026, 11, 9, 10, 0));
        Pedido nuevo = pedido("FINALIZADO", juan, "3814111111", local(2026, 11, 10, 10, 0));

        assertThat(numeros(filtro().armar()))
                .containsExactly(nuevo.getNumero(), viejo.getNumero());
    }

    @Test
    void filtraPorNumeroTelefonoYCadete() {
        Pedido a = pedido("FINALIZADO", juan, "3814111111", local(2026, 11, 10, 10, 0));
        Pedido b = pedido("FINALIZADO", pedro, "3814222222", local(2026, 11, 10, 11, 0));
        pedido("SIN_ASIGNAR", null, "3814111111", local(2026, 11, 10, 12, 0));

        assertThat(numeros(filtro().numero(b.getNumero()).armar())).containsExactly(b.getNumero());
        assertThat(numeros(filtro().telefono("3814222222").armar())).containsExactly(b.getNumero());
        assertThat(numeros(filtro().cadete(juan.getId()).armar())).containsExactly(a.getNumero());
        assertThat(numeros(filtro().telefono("3814111111").cadete(juan.getId()).armar())).containsExactly(a.getNumero());
    }

    @Test
    void enCursoYRetiradoSeSeparanPorSiYaRetiro() {
        Pedido sinRetirar = pedido("EN_CURSO", juan, "3814111111", local(2026, 11, 10, 10, 0));
        Pedido retirado = pedido("EN_CURSO", juan, "3814111111", local(2026, 11, 10, 11, 0));
        retirado.setRetiradoEn(local(2026, 11, 10, 11, 20));
        em.persistAndFlush(retirado);
        Pedido pendiente = pedido("PENDIENTE", juan, "3814111111", local(2026, 11, 10, 12, 0));
        Pedido anulado = pedido("CANCELADO", null, "3814111111", local(2026, 11, 10, 13, 0));

        assertThat(numeros(filtro().estado("EN_CURSO", false).armar())).containsExactly(sinRetirar.getNumero());
        assertThat(numeros(filtro().estado("EN_CURSO", true).armar())).containsExactly(retirado.getNumero());
        assertThat(numeros(filtro().estado("PENDIENTE", null).armar())).containsExactly(pendiente.getNumero());
        assertThat(numeros(filtro().estado("CANCELADO", null).armar())).containsExactly(anulado.getNumero());
    }

    @Test
    void rangoDeFechasSobreLaCreacion() {
        pedido("FINALIZADO", juan, "3814111111", local(2026, 11, 9, 23, 59));
        Pedido delDia = pedido("FINALIZADO", juan, "3814111111", local(2026, 11, 10, 0, 0));
        pedido("FINALIZADO", juan, "3814111111", local(2026, 11, 11, 0, 0));

        BusquedaPedidos f = filtro().entre(local(2026, 11, 10, 0, 0), local(2026, 11, 11, 0, 0)).armar();

        assertThat(numeros(f)).containsExactly(delDia.getNumero());
    }

    @Test
    void rangoHorarioEnHoraArgentinaDeCualquierDia() {
        pedido("FINALIZADO", juan, "3814111111", local(2026, 11, 9, 13, 59));
        Pedido a = pedido("FINALIZADO", juan, "3814111111", local(2026, 11, 9, 14, 0));
        Pedido b = pedido("FINALIZADO", juan, "3814111111", local(2026, 11, 10, 15, 30));
        Pedido c = pedido("FINALIZADO", juan, "3814111111", local(2026, 11, 10, 16, 0));
        pedido("FINALIZADO", juan, "3814111111", local(2026, 11, 10, 16, 1));
        // 22:30 en Argentina es 01:30 UTC del día siguiente: tiene que contar como 22:30.
        pedido("FINALIZADO", juan, "3814111111", local(2026, 11, 10, 22, 30));

        BusquedaPedidos f = filtro().horas(LocalTime.of(14, 0), LocalTime.of(16, 0)).armar();

        assertThat(numeros(f)).containsExactly(c.getNumero(), b.getNumero(), a.getNumero());
    }

    @Test
    void rangoHorarioQueCruzaLaMedianoche() {
        Pedido noche = pedido("FINALIZADO", juan, "3814111111", local(2026, 11, 10, 23, 15));
        Pedido madrugada = pedido("FINALIZADO", juan, "3814111111", local(2026, 11, 11, 1, 0));
        pedido("FINALIZADO", juan, "3814111111", local(2026, 11, 11, 12, 0));

        BusquedaPedidos f = filtro().horas(LocalTime.of(22, 0), LocalTime.of(2, 0)).armar();

        assertThat(numeros(f)).containsExactly(madrugada.getNumero(), noche.getNumero());
    }

    @Test
    void pagina() {
        for (int i = 0; i < 5; i++) {
            pedido("FINALIZADO", juan, "3814111111", local(2026, 11, 10, 10, i));
        }
        var pagina = repo.buscar(filtro().armar(), PageRequest.of(1, 2));

        assertThat(pagina.getTotalElements()).isEqualTo(5);
        assertThat(pagina.getTotalPages()).isEqualTo(3);
        assertThat(pagina.getContent()).hasSize(2);
    }

    @Test
    void sugiereClientesPorNombreOTelefonoDeLosPedidos() {
        Pedido a = pedido("FINALIZADO", juan, "3814111111", local(2026, 11, 9, 10, 0));
        a.setClienteNombre("Juana Gómez");
        em.persistAndFlush(a);
        Pedido b = pedido("FINALIZADO", juan, "3815222222", local(2026, 11, 10, 10, 0));
        b.setClienteNombre("Pedro Juárez");
        em.persistAndFlush(b);

        var porNombre = repo.clientesSugeridos("juan", 10);
        assertThat(porNombre).extracting(PedidoRepository.ClienteSugeridoRow::getTelefono).containsExactly("3814111111");
        assertThat(porNombre.get(0).getNombre()).isEqualTo("Juana Gómez");

        var porTelefono = repo.clientesSugeridos("15222", 10);
        assertThat(porTelefono).extracting(PedidoRepository.ClienteSugeridoRow::getNombre).containsExactly("Pedro Juárez");
    }

    // ---- ayudas ----

    private List<Long> numeros(BusquedaPedidos f) {
        return repo.buscar(f, PageRequest.of(0, 50)).getContent().stream().map(Pedido::getNumero).toList();
    }

    private static Instant local(int anio, int mes, int dia, int hora, int minuto) {
        return LocalDateTime.of(anio, mes, dia, hora, minuto).atZone(ZONA).toInstant();
    }

    private Cadete cadete(String nombre, String dni, TipoVehiculo vehiculo, EstadoCadete estado) {
        Cadete c = new Cadete();
        c.setId(UUID.randomUUID().toString());
        c.setNombre(nombre);
        c.setApellido("Prueba");
        c.setDni(dni);
        c.setTelefono("381" + dni.substring(1));
        c.setUsername(dni);
        c.setPasswordHash("x");
        c.setTipoVehiculo(vehiculo);
        c.setEstado(estado);
        return em.persistAndFlush(c);
    }

    private Pedido pedido(String estado, Cadete cadete, String telefono, Instant creadoEn) {
        Pedido p = new Pedido();
        p.setId(UUID.randomUUID().toString());
        p.setNumero(siguienteNumero++);
        p.setClienteTelefono(telefono);
        p.setClienteNombre("Cliente " + telefono);
        p.setOrigenDireccion("Origen 100");
        p.setOrigenLat(-26.8);
        p.setOrigenLng(-65.2);
        p.setDestinoDireccion("Destino 200");
        p.setDestinoLat(-26.81);
        p.setDestinoLng(-65.21);
        p.setPrecio(new BigDecimal("1500"));
        p.setEstado(em.find(EstadoPedido.class, estado));
        p.setCadeteAsignado(cadete);
        p.setCreadoEn(creadoEn);
        p.setTokenSeguimiento(UUID.randomUUID().toString());
        return em.persistAndFlush(p);
    }

    private static Constructor filtro() {
        return new Constructor();
    }

    /** Arma el filtro de a partes para que cada prueba muestre solo lo que le importa. */
    private static class Constructor {
        private Long numero;
        private String telefono;
        private String cadeteId;
        private String estadoId;
        private Boolean retirado;
        private Instant desde;
        private Instant hasta;
        private LocalTime horaDesde;
        private LocalTime horaHasta;

        Constructor numero(Long n) { numero = n; return this; }
        Constructor telefono(String t) { telefono = t; return this; }
        Constructor cadete(String id) { cadeteId = id; return this; }
        Constructor estado(String id, Boolean conRetiro) { estadoId = id; retirado = conRetiro; return this; }
        Constructor entre(Instant d, Instant h) { desde = d; hasta = h; return this; }
        Constructor horas(LocalTime d, LocalTime h) { horaDesde = d; horaHasta = h; return this; }

        BusquedaPedidos armar() {
            return BusquedaPedidos.de(numero, telefono, cadeteId, estadoId, retirado, desde, hasta, horaDesde, horaHasta);
        }
    }
}
