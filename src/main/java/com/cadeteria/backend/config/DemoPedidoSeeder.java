package com.cadeteria.backend.config;

import com.cadeteria.backend.model.Cadete;
import com.cadeteria.backend.model.CadeteSesion;
import com.cadeteria.backend.model.EstadoCadete;
import com.cadeteria.backend.model.EstadoPedido;
import com.cadeteria.backend.model.OfertaPedido;
import com.cadeteria.backend.model.Pedido;
import com.cadeteria.backend.model.ResultadoOferta;
import com.cadeteria.backend.model.TipoVehiculo;
import com.cadeteria.backend.model.Zona;
import com.cadeteria.backend.repository.CadeteRepository;
import com.cadeteria.backend.repository.CadeteSesionRepository;
import com.cadeteria.backend.repository.EstadoCadeteRepository;
import com.cadeteria.backend.repository.EstadoPedidoRepository;
import com.cadeteria.backend.repository.OfertaPedidoRepository;
import com.cadeteria.backend.repository.PedidoComentarioRepository;
import com.cadeteria.backend.repository.PedidoRepository;
import com.cadeteria.backend.repository.PedidoUbicacionRepository;
import com.cadeteria.backend.repository.ResultadoOfertaRepository;
import com.cadeteria.backend.repository.TipoVehiculoRepository;
import com.cadeteria.backend.repository.ZonaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * Pedidos de ejemplo para poder mostrarle el dashboard y las Métricas a un cliente
 * potencial sin depender de que haya movimiento real cargado ese día: cubre cada
 * estado del ciclo de vida de un pedido (sin asignar, ofertado/pendiente de aceptar,
 * en curso recién aceptado, en curso ya retirado, finalizado con los tres horarios
 * cargados, y cancelado por los dos motivos posibles).
 *
 * Se identifican por id fijo (no UUID random) y se recrean con horarios relativos a
 * "ahora" en cada arranque del backend (se borran e insertan de nuevo) — así el
 * dashboard y Métricas siempre tienen algo del día para mostrar, sin acumular
 * duplicados en reinicios sucesivos ni tocar los pedidos cargados a mano por el
 * cadete/admin real. Requiere que ya exista al menos un cadete y una zona dados de
 * alta — en una base recién creada los siembra {@link DemoCadeteZonaSeeder}, que corre
 * antes (Order 2) para que este seeder siempre tenga a quién asignarle los pedidos.
 */
@Component
@Order(3)
public class DemoPedidoSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoPedidoSeeder.class);
    private static final String CADETE_USERNAME_PREFERIDO = "jperez";

    private static final List<String> PEDIDO_IDS = List.of(
            "demo-pedido-01", "demo-pedido-02", "demo-pedido-03", "demo-pedido-04",
            "demo-pedido-05", "demo-pedido-06", "demo-pedido-07", "demo-pedido-08");
    private static final List<String> OFERTA_IDS = List.of(
            "demo-oferta-01", "demo-oferta-02", "demo-oferta-03", "demo-oferta-04", "demo-oferta-05");
    private static final String SESION_ID = "demo-sesion-cadete";

    private final PedidoRepository pedidoRepo;
    private final OfertaPedidoRepository ofertaRepo;
    private final PedidoComentarioRepository comentarioRepo;
    private final PedidoUbicacionRepository ubicacionRepo;
    private final CadeteRepository cadeteRepo;
    private final ZonaRepository zonaRepo;
    private final TipoVehiculoRepository tipoVehiculoRepo;
    private final EstadoPedidoRepository estadoPedidoRepo;
    private final ResultadoOfertaRepository resultadoOfertaRepo;
    private final EstadoCadeteRepository estadoCadeteRepo;
    private final CadeteSesionRepository sesionRepo;
    private final AppProperties props;

    public DemoPedidoSeeder(PedidoRepository pedidoRepo, OfertaPedidoRepository ofertaRepo,
                             PedidoComentarioRepository comentarioRepo, PedidoUbicacionRepository ubicacionRepo,
                             CadeteRepository cadeteRepo, ZonaRepository zonaRepo,
                             TipoVehiculoRepository tipoVehiculoRepo, EstadoPedidoRepository estadoPedidoRepo,
                             ResultadoOfertaRepository resultadoOfertaRepo, EstadoCadeteRepository estadoCadeteRepo,
                             CadeteSesionRepository sesionRepo, AppProperties props) {
        this.pedidoRepo = pedidoRepo;
        this.ofertaRepo = ofertaRepo;
        this.comentarioRepo = comentarioRepo;
        this.ubicacionRepo = ubicacionRepo;
        this.cadeteRepo = cadeteRepo;
        this.zonaRepo = zonaRepo;
        this.tipoVehiculoRepo = tipoVehiculoRepo;
        this.estadoPedidoRepo = estadoPedidoRepo;
        this.resultadoOfertaRepo = resultadoOfertaRepo;
        this.estadoCadeteRepo = estadoCadeteRepo;
        this.sesionRepo = sesionRepo;
        this.props = props;
    }

    @Override
    public void run(String... args) {
        if (!props.getDemo().isEnabled()) return;

        Optional<Cadete> cadeteOpt = cadeteRepo.findByUsername(CADETE_USERNAME_PREFERIDO)
                .or(() -> cadeteRepo.findAll().stream().findFirst());
        Optional<Zona> zonaOpt = zonaRepo.findAll().stream().findFirst();
        Optional<TipoVehiculo> motoOpt = tipoVehiculoRepo.findById("MOTO");

        if (cadeteOpt.isEmpty() || zonaOpt.isEmpty() || motoOpt.isEmpty()) {
            log.info("Demo: todavia no hay cadete/zona cargados a mano, no se siembran pedidos de ejemplo.");
            return;
        }

        Cadete cadete = cadeteOpt.get();
        Zona zona = zonaOpt.get();
        TipoVehiculo moto = motoOpt.get();

        limpiarDemoAnterior();

        Instant ahora = Instant.now();

        // 1) El pedido "igual" al que ya está finalizado (Monteros -> San Miguel de Tucumán, mismo
        // recorrido/precio/monto declarado), pero recorriendo el ciclo completo con los tres horarios.
        crearFinalizado("demo-pedido-01", "demo-oferta-01", 9_100_001L, cadete, zona, moto,
                "Marcos Herrera", "3813012345",
                "25 de Mayo 231, Monteros", -27.1616857, -65.5061631,
                "San Juan 354, San Miguel de Tucumán", -26.826078, -65.2011654,
                new BigDecimal("1222.00"), new BigDecimal("50000.00"), "Lucía Fernández",
                ahora.minus(50, ChronoUnit.MINUTES), ahora.minus(48, ChronoUnit.MINUTES),
                ahora.minus(46, ChronoUnit.MINUTES), ahora.minus(33, ChronoUnit.MINUTES), ahora.minus(15, ChronoUnit.MINUTES));

        // 2) Otro viaje finalizado antes, para que Métricas sume más de un viaje/más km.
        crearFinalizado("demo-pedido-02", "demo-oferta-02", 9_100_002L, cadete, zona, moto,
                "Carla Sosa", "3814098765",
                "Av. Aconquija 1200, Yerba Buena", -26.8161, -65.3086,
                "San Martín 500, San Miguel de Tucumán", -26.8285, -65.2038,
                new BigDecimal("950.00"), BigDecimal.ZERO, "Pedro Ibáñez",
                ahora.minus(120, ChronoUnit.MINUTES), ahora.minus(118, ChronoUnit.MINUTES),
                ahora.minus(117, ChronoUnit.MINUTES), ahora.minus(108, ChronoUnit.MINUTES), ahora.minus(92, ChronoUnit.MINUTES));

        // 3) Recién ofertado: el cadete todavía no lo acepto ni lo rechazo (estado PENDIENTE = "asignado").
        Pedido p3 = base("demo-pedido-03", 9_100_003L, cadete, zona, moto,
                "Julieta Paz", "3815551122",
                "Mendoza 800, San Miguel de Tucumán", -26.8241, -65.2065,
                "Congreso 350, San Miguel de Tucumán", -26.8175, -65.1974,
                new BigDecimal("700.00"), BigDecimal.ZERO);
        p3.setEstado(estadoPedido("PENDIENTE"));
        p3.setAsignadoEn(ahora.minus(30, ChronoUnit.SECONDS));
        pedidoRepo.save(p3);
        crearOferta("demo-oferta-03", p3, cadete, "PENDIENTE", ahora.minus(30, ChronoUnit.SECONDS), ahora.plus(90, ChronoUnit.SECONDS));

        // 4) Aceptado por el cadete, todavía no marco "Retirado".
        Pedido p4 = base("demo-pedido-04", 9_100_004L, cadete, zona, moto,
                "Nicolás Ávila", "3815223344",
                "Las Piedras 250, San Miguel de Tucumán", -26.8302, -65.2103,
                "Av. Sarmiento 900, San Miguel de Tucumán", -26.8198, -65.1988,
                new BigDecimal("850.00"), new BigDecimal("15000.00"));
        p4.setEstado(estadoPedido("EN_CURSO"));
        p4.setAsignadoEn(ahora.minus(7, ChronoUnit.MINUTES));
        p4.setAceptadoEn(ahora.minus(5, ChronoUnit.MINUTES));
        pedidoRepo.save(p4);
        crearOferta("demo-oferta-04", p4, cadete, "ACEPTADO", ahora.minus(7, ChronoUnit.MINUTES), ahora.minus(5, ChronoUnit.MINUTES));

        // 5) Ya marco "Retirado", en camino al destino.
        Pedido p5 = base("demo-pedido-05", 9_100_005L, cadete, zona, moto,
                "Sofía Molina", "3815667788",
                "Balcarce 700, San Miguel de Tucumán", -26.8226, -65.2049,
                "Barrio Sur, San Miguel de Tucumán", -26.8465, -65.2159,
                new BigDecimal("1100.00"), new BigDecimal("8000.00"));
        p5.setEstado(estadoPedido("EN_CURSO"));
        p5.setAsignadoEn(ahora.minus(22, ChronoUnit.MINUTES));
        p5.setAceptadoEn(ahora.minus(20, ChronoUnit.MINUTES));
        p5.setRetiradoEn(ahora.minus(8, ChronoUnit.MINUTES));
        p5.setRetiroLat(-26.8226);
        p5.setRetiroLng(-65.2049);
        pedidoRepo.save(p5);
        crearOferta("demo-oferta-05", p5, cadete, "ACEPTADO", ahora.minus(22, ChronoUnit.MINUTES), ahora.minus(20, ChronoUnit.MINUTES));

        // 6) Recién cargado, todavía sin cadete asignado (para la columna "Asignar" del dashboard).
        Pedido p6 = base("demo-pedido-06", 9_100_006L, null, zona, moto,
                "Emilia Torres", "3815884455",
                "Av. Roca 1500, San Miguel de Tucumán", -26.8402, -65.2245,
                "Complejo Este, San Miguel de Tucumán", -26.8092, -65.1901,
                new BigDecimal("780.00"), BigDecimal.ZERO);
        p6.setEstado(estadoPedido("SIN_ASIGNAR"));
        pedidoRepo.save(p6);

        // 7) Cancelado por el cliente.
        Pedido p7 = base("demo-pedido-07", 9_100_007L, null, zona, moto,
                "Ramiro Costa", "3815990011",
                "Junín 400, San Miguel de Tucumán", -26.8261, -65.2087,
                "Barrio Norte, San Miguel de Tucumán", -26.8010, -65.2100,
                new BigDecimal("650.00"), BigDecimal.ZERO);
        p7.setEstado(estadoPedido("CANCELADO"));
        p7.setMotivoCancelacion("CLIENTE");
        p7.setCanceladoEn(ahora.minus(70, ChronoUnit.MINUTES));
        pedidoRepo.save(p7);

        // 8) Cancelado por otro motivo (ej. no habia cadete disponible en la zona).
        Pedido p8 = base("demo-pedido-08", 9_100_008L, null, zona, moto,
                "Valentina Ruiz", "3815223399",
                "Alberdi 900, San Miguel de Tucumán", -26.8305, -65.2181,
                "El Manantial", -26.9331, -65.3128,
                new BigDecimal("1600.00"), BigDecimal.ZERO);
        p8.setEstado(estadoPedido("CANCELADO"));
        p8.setMotivoCancelacion("OTRO");
        p8.setCanceladoEn(ahora.minus(100, ChronoUnit.MINUTES));
        pedidoRepo.save(p8);

        // El cadete queda "OCUPADO" porque tiene los pedidos 3/4/5 activos, igual que pasaria en la app real.
        cadete.setEstado(estadoCadete("OCUPADO"));
        cadeteRepo.save(cadete);

        CadeteSesion sesion = new CadeteSesion();
        sesion.setId(SESION_ID);
        sesion.setCadete(cadete);
        sesion.setConectadoEn(ahora.minus(2, ChronoUnit.HOURS));
        sesionRepo.save(sesion);

        log.info("Demo: {} pedidos de ejemplo sembrados (todos los estados) para el cadete '{}'.",
                PEDIDO_IDS.size(), cadete.getUsername());
    }

    /**
     * Antes de recrear los pedidos demo, borra TODAS las filas que los referencian (ofertas,
     * comentarios, puntos de ubicación) — no solo las de OFERTA_IDS. Si mientras corrió el
     * backend se generó actividad real contra uno de estos pedidos (ej. "asignacion_automatica"
     * prendida los volvió a ofertar, o el cadete dejó un comentario), esas filas igual tienen
     * FK contra el pedido demo y bloquean el DELETE de abajo.
     */
    private void limpiarDemoAnterior() {
        PEDIDO_IDS.forEach(id -> ofertaRepo.findByPedidoId(id).forEach(ofertaRepo::delete));
        PEDIDO_IDS.forEach(id -> comentarioRepo.findByPedidoIdOrderByCreadoEnAsc(id).forEach(comentarioRepo::delete));
        PEDIDO_IDS.forEach(id -> ubicacionRepo.findByPedidoIdOrderByCapturadoEnAsc(id).forEach(ubicacionRepo::delete));
        PEDIDO_IDS.forEach(id -> pedidoRepo.findById(id).ifPresent(pedidoRepo::delete));
        sesionRepo.findById(SESION_ID).ifPresent(sesionRepo::delete);
    }

    private Pedido base(String id, long numero, Cadete cadete, Zona zona, TipoVehiculo tipo,
                         String clienteNombre, String clienteTelefono,
                         String origenDireccion, double origenLat, double origenLng,
                         String destinoDireccion, double destinoLat, double destinoLng,
                         BigDecimal precio, BigDecimal montoDeclarado) {
        Pedido p = new Pedido();
        p.setId(id);
        p.setNumero(numero);
        p.setTokenSeguimiento("demo-token-" + id);
        p.setClienteNombre(clienteNombre);
        p.setClienteTelefono(clienteTelefono);
        p.setOrigenDireccion(origenDireccion);
        p.setOrigenLat(origenLat);
        p.setOrigenLng(origenLng);
        p.setDestinoDireccion(destinoDireccion);
        p.setDestinoLat(destinoLat);
        p.setDestinoLng(destinoLng);
        p.setPrecio(precio);
        p.setMontoDeclarado(montoDeclarado);
        p.setZona(zona);
        p.setTipoVehiculoRequerido(tipo);
        p.setCadeteAsignado(cadete);
        p.setCreadoEn(Instant.now());
        return p;
    }

    private void crearFinalizado(String id, String ofertaId, long numero, Cadete cadete, Zona zona, TipoVehiculo tipo,
                                  String clienteNombre, String clienteTelefono,
                                  String origenDireccion, double origenLat, double origenLng,
                                  String destinoDireccion, double destinoLat, double destinoLng,
                                  BigDecimal precio, BigDecimal montoDeclarado, String receptorNombre,
                                  Instant creadoEn, Instant asignadoEn, Instant aceptadoEn,
                                  Instant retiradoEn, Instant finalizadoEn) {
        Pedido p = base(id, numero, cadete, zona, tipo, clienteNombre, clienteTelefono,
                origenDireccion, origenLat, origenLng, destinoDireccion, destinoLat, destinoLng, precio, montoDeclarado);
        p.setCreadoEn(creadoEn);
        p.setAsignadoEn(asignadoEn);
        p.setAceptadoEn(aceptadoEn);
        p.setRetiradoEn(retiradoEn);
        p.setRetiroLat(origenLat);
        p.setRetiroLng(origenLng);
        p.setFinalizadoEn(finalizadoEn);
        p.setEntregaReceptorNombre(receptorNombre);
        p.setEntregaLat(destinoLat);
        p.setEntregaLng(destinoLng);
        p.setEstado(estadoPedido("FINALIZADO"));
        pedidoRepo.save(p);
        crearOferta(ofertaId, p, cadete, "ACEPTADO", asignadoEn, aceptadoEn);
    }

    private void crearOferta(String id, Pedido pedido, Cadete cadete, String resultadoId, Instant ofrecidoEn, Instant expiraEn) {
        OfertaPedido o = new OfertaPedido();
        o.setId(id);
        o.setPedido(pedido);
        o.setCadete(cadete);
        o.setOfrecidoEn(ofrecidoEn);
        o.setExpiraEn(expiraEn);
        o.setResultado(resultado(resultadoId));
        ofertaRepo.save(o);
    }

    private EstadoPedido estadoPedido(String id) {
        return estadoPedidoRepo.findById(id)
                .orElseThrow(() -> new IllegalStateException("Falta seedear estado_pedido." + id));
    }

    private ResultadoOferta resultado(String id) {
        return resultadoOfertaRepo.findById(id)
                .orElseThrow(() -> new IllegalStateException("Falta seedear resultado_oferta." + id));
    }

    private EstadoCadete estadoCadete(String id) {
        return estadoCadeteRepo.findById(id)
                .orElseThrow(() -> new IllegalStateException("Falta seedear estado_cadete." + id));
    }
}
