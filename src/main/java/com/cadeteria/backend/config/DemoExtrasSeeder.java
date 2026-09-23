package com.cadeteria.backend.config;

import com.cadeteria.backend.model.Admin;
import com.cadeteria.backend.model.AutorMensaje;
import com.cadeteria.backend.model.Cadete;
import com.cadeteria.backend.model.ChatMensaje;
import com.cadeteria.backend.model.Incidencia;
import com.cadeteria.backend.model.PagoSemanal;
import com.cadeteria.backend.model.SolicitudCadete;
import com.cadeteria.backend.model.SolicitudPedido;
import com.cadeteria.backend.model.TipoVehiculo;
import com.cadeteria.backend.model.Zona;
import com.cadeteria.backend.repository.AdminRepository;
import com.cadeteria.backend.repository.AutorMensajeRepository;
import com.cadeteria.backend.repository.CadeteRepository;
import com.cadeteria.backend.repository.ChatMensajeRepository;
import com.cadeteria.backend.repository.IncidenciaRepository;
import com.cadeteria.backend.repository.PagoSemanalRepository;
import com.cadeteria.backend.repository.SolicitudCadeteRepository;
import com.cadeteria.backend.repository.SolicitudPedidoRepository;
import com.cadeteria.backend.repository.TipoVehiculoRepository;
import com.cadeteria.backend.repository.ZonaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Resto de las pantallas del panel que quedaban vacías con solo pedidos de ejemplo
 * (ver {@link DemoPedidoSeeder}): chat interno, el flujo de "pedido pedido por WhatsApp
 * sin login" (SolicitudPedido), una postulación de cadete pendiente de revisión, una
 * incidencia y el pago semanal — para que un cliente potencial vea el panel completo,
 * no solo el dashboard de pedidos.
 *
 * Mismo criterio que DemoPedidoSeeder: ids fijos, se borran y recrean en cada arranque
 * con horarios relativos a "ahora".
 */
@Component
@Order(4)
public class DemoExtrasSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoExtrasSeeder.class);
    private static final ZoneId ZONA_ART = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final String CADETE_USERNAME_PREFERIDO = "jperez";

    private static final List<String> CHAT_IDS = List.of(
            "demo-chat-01", "demo-chat-02", "demo-chat-03", "demo-chat-04");
    private static final String SOLICITUD_CADETE_ID = "demo-solicitud-cadete-01";
    private static final List<String> SOLICITUD_PEDIDO_IDS = List.of(
            "demo-solicitud-pedido-01", "demo-solicitud-pedido-02");
    private static final String INCIDENCIA_ID = "demo-incidencia-01";
    private static final List<String> PAGO_IDS = List.of("demo-pago-01", "demo-pago-02");

    private final ChatMensajeRepository chatRepo;
    private final SolicitudCadeteRepository solicitudCadeteRepo;
    private final SolicitudPedidoRepository solicitudPedidoRepo;
    private final IncidenciaRepository incidenciaRepo;
    private final PagoSemanalRepository pagoSemanalRepo;
    private final CadeteRepository cadeteRepo;
    private final ZonaRepository zonaRepo;
    private final TipoVehiculoRepository tipoVehiculoRepo;
    private final AutorMensajeRepository autorMensajeRepo;
    private final AdminRepository adminRepo;
    private final AppProperties props;

    public DemoExtrasSeeder(ChatMensajeRepository chatRepo, SolicitudCadeteRepository solicitudCadeteRepo,
                             SolicitudPedidoRepository solicitudPedidoRepo, IncidenciaRepository incidenciaRepo,
                             PagoSemanalRepository pagoSemanalRepo, CadeteRepository cadeteRepo,
                             ZonaRepository zonaRepo, TipoVehiculoRepository tipoVehiculoRepo,
                             AutorMensajeRepository autorMensajeRepo, AdminRepository adminRepo,
                             AppProperties props) {
        this.chatRepo = chatRepo;
        this.solicitudCadeteRepo = solicitudCadeteRepo;
        this.solicitudPedidoRepo = solicitudPedidoRepo;
        this.incidenciaRepo = incidenciaRepo;
        this.pagoSemanalRepo = pagoSemanalRepo;
        this.cadeteRepo = cadeteRepo;
        this.zonaRepo = zonaRepo;
        this.tipoVehiculoRepo = tipoVehiculoRepo;
        this.autorMensajeRepo = autorMensajeRepo;
        this.adminRepo = adminRepo;
        this.props = props;
    }

    @Override
    public void run(String... args) {
        if (!props.getDemo().isEnabled()) return;

        Optional<Cadete> cadeteOpt = cadeteRepo.findByUsername(CADETE_USERNAME_PREFERIDO)
                .or(() -> cadeteRepo.findAll().stream().findFirst());
        Optional<Zona> zonaOpt = zonaRepo.findAll().stream().findFirst();
        Optional<TipoVehiculo> motoOpt = tipoVehiculoRepo.findById("MOTO");
        Optional<Admin> adminOpt = adminRepo.findAll().stream().findFirst();

        if (cadeteOpt.isEmpty() || zonaOpt.isEmpty() || motoOpt.isEmpty() || adminOpt.isEmpty()) {
            log.info("Demo: todavia no hay cadete/zona/admin cargados, no se siembran los extras del panel.");
            return;
        }

        Cadete cadete = cadeteOpt.get();
        Zona zona = zonaOpt.get();
        TipoVehiculo moto = motoOpt.get();
        Admin admin = adminOpt.get();

        limpiarDemoAnterior();

        Instant ahora = Instant.now();

        seedChat(cadete, ahora);
        seedSolicitudCadete(ahora);
        seedSolicitudesPedido(zona, moto, ahora);
        seedIncidencia(cadete, admin, ahora);
        seedPagosSemanales(cadete, admin, ahora);

        log.info("Demo: chat, solicitudes, incidencia y pagos semanales sembrados.");
    }

    private void limpiarDemoAnterior() {
        CHAT_IDS.forEach(id -> chatRepo.findById(id).ifPresent(chatRepo::delete));
        solicitudCadeteRepo.findById(SOLICITUD_CADETE_ID).ifPresent(solicitudCadeteRepo::delete);
        SOLICITUD_PEDIDO_IDS.forEach(id -> solicitudPedidoRepo.findById(id).ifPresent(solicitudPedidoRepo::delete));
        incidenciaRepo.findById(INCIDENCIA_ID).ifPresent(incidenciaRepo::delete);
        PAGO_IDS.forEach(id -> pagoSemanalRepo.findById(id).ifPresent(pagoSemanalRepo::delete));
    }

    private void seedChat(Cadete cadete, Instant ahora) {
        AutorMensaje admin = autorMensaje("ADMIN");
        AutorMensaje cadeteAutor = autorMensaje("CADETE");

        chatMensaje("demo-chat-01", cadete, admin, "Hola! ¿Cómo va todo por ahí?",
                ahora.minus(3, ChronoUnit.HOURS), true);
        chatMensaje("demo-chat-02", cadete, cadeteAutor, "Todo bien, ya salgo para el primer pedido",
                ahora.minus(170, ChronoUnit.MINUTES), true);
        chatMensaje("demo-chat-03", cadete, admin, "Dale, cualquier cosa avisame",
                ahora.minus(165, ChronoUnit.MINUTES), true);
        chatMensaje("demo-chat-04", cadete, cadeteAutor, "Se complicó bastante el tránsito por la 24 de Septiembre",
                ahora.minus(10, ChronoUnit.MINUTES), false);
    }

    private void chatMensaje(String id, Cadete cadete, AutorMensaje autor, String texto, Instant enviadoEn, boolean leido) {
        ChatMensaje m = new ChatMensaje();
        m.setId(id);
        m.setCadete(cadete);
        m.setAutor(autor);
        m.setTexto(texto);
        m.setEnviadoEn(enviadoEn);
        m.setLeido(leido);
        chatRepo.save(m);
    }

    /** Postulante que completó el formulario público y espera que el admin lo revise. */
    private void seedSolicitudCadete(Instant ahora) {
        SolicitudCadete s = new SolicitudCadete();
        s.setId(SOLICITUD_CADETE_ID);
        s.setToken(UUID.randomUUID().toString());
        s.setEstado("EN_REVISION");
        s.setCreadoEn(ahora.minus(20, ChronoUnit.HOURS));
        s.setExpiraEn(ahora.plus(6, ChronoUnit.DAYS));
        s.setEnviadaEn(ahora.minus(18, ChronoUnit.HOURS));
        s.setNombre("Lucas");
        s.setApellido("Medina");
        s.setDni("30333444");
        s.setTelefono("3815556677");
        s.setEmail("lucas.medina@demo.local");
        s.setTipoVehiculo(tipoVehiculoRepo.findById("MOTO").orElse(null));
        s.setVehiculoColor("Roja");
        s.setVehiculoPatente("B456XYZ");
        s.setVehiculoMarca("Yamaha");
        s.setVehiculoModelo("Crypton");
        s.setUsernamePropuesto("lmedina");
        solicitudCadeteRepo.save(s);
    }

    /** El flujo de "pedido por WhatsApp": el cliente lo carga solo desde el link público que el admin le pasa por chat. */
    private void seedSolicitudesPedido(Zona zona, TipoVehiculo moto, Instant ahora) {
        SolicitudPedido pendiente = new SolicitudPedido();
        pendiente.setId("demo-solicitud-pedido-01");
        pendiente.setOrigenDireccion("Av. Mate de Luna 1800, San Miguel de Tucumán");
        pendiente.setOrigenLat(-26.8135);
        pendiente.setOrigenLng(-65.2245);
        pendiente.setDestinoDireccion("Barrio Norte, San Miguel de Tucumán");
        pendiente.setDestinoLat(-26.8010);
        pendiente.setDestinoLng(-65.2100);
        pendiente.setLlevaDinero(false);
        pendiente.setRetornaAlOrigen(false);
        pendiente.setClienteNombre("Diego Fernández");
        pendiente.setClienteTelefono("3815441122");
        pendiente.setDetalle("Es urgente, hay que retirar unos documentos");
        pendiente.setEstado("PENDIENTE");
        pendiente.setTokenConfirmacion(UUID.randomUUID().toString());
        pendiente.setCreadoEn(ahora.minus(6, ChronoUnit.MINUTES));
        solicitudPedidoRepo.save(pendiente);

        SolicitudPedido cotizada = new SolicitudPedido();
        cotizada.setId("demo-solicitud-pedido-02");
        cotizada.setOrigenDireccion("Congreso 200, San Miguel de Tucumán");
        cotizada.setOrigenLat(-26.8175);
        cotizada.setOrigenLng(-65.1974);
        cotizada.setDestinoDireccion("Centro de Yerba Buena");
        cotizada.setDestinoLat(-26.8161);
        cotizada.setDestinoLng(-65.3086);
        cotizada.setLlevaDinero(true);
        cotizada.setMontoDeclarado(new BigDecimal("20000.00"));
        cotizada.setRetornaAlOrigen(false);
        cotizada.setClienteNombre("Marina López");
        cotizada.setClienteTelefono("3815778899");
        cotizada.setDetalle("Envío de repuestos");
        cotizada.setEstado("COTIZADO");
        cotizada.setTokenConfirmacion(UUID.randomUUID().toString());
        cotizada.setRequiereMoto(moto != null && "MOTO".equals(moto.getId()));
        cotizada.setPrecio(new BigDecimal("1350.00"));
        cotizada.setCreadoEn(ahora.minus(40, ChronoUnit.MINUTES));
        solicitudPedidoRepo.save(cotizada);
    }

    /** Ligada a un pedido finalizado real (demo-pedido-02) para mostrar la trazabilidad desde su detalle. */
    private void seedIncidencia(Cadete cadete, Admin admin, Instant ahora) {
        Incidencia i = new Incidencia();
        i.setId(INCIDENCIA_ID);
        i.setTitulo("Cliente reclama demora en la entrega");
        i.setDescripcion("El cliente llamó porque el envío tardó más de lo esperado. Se le explicó la demora por tránsito.");
        i.setEstado("ABIERTA");
        i.setPrioridad("NORMAL");
        i.setCadeteId(cadete.getId());
        i.setCadeteNombre(cadete.getNombre() + " " + cadete.getApellido());
        i.setCreadaEn(ahora.minus(45, ChronoUnit.MINUTES));
        i.setCreadaPorUsername(admin.getUsername());
        i.setPedidoId("demo-pedido-02");
        i.setPedidoNumero(9_100_002L);
        incidenciaRepo.save(i);
    }

    /** Semana actual pendiente de cobro + la semana pasada ya pagada, para que la pantalla de Pagos no esté vacía. */
    private void seedPagosSemanales(Cadete cadete, Admin admin, Instant ahora) {
        LocalDate lunesActual = LocalDate.now(ZONA_ART).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

        PagoSemanal pendiente = new PagoSemanal();
        pendiente.setId("demo-pago-01");
        pendiente.setCadete(cadete);
        pendiente.setSemanaInicio(lunesActual);
        pendiente.setPagado(false);
        pagoSemanalRepo.save(pendiente);

        PagoSemanal pagado = new PagoSemanal();
        pagado.setId("demo-pago-02");
        pagado.setCadete(cadete);
        pagado.setSemanaInicio(lunesActual.minusWeeks(1));
        pagado.setPagado(true);
        pagado.setRegistradoPor(admin);
        pagado.setRegistradoEn(ahora.minus(6, ChronoUnit.DAYS));
        pagoSemanalRepo.save(pagado);
    }

    private AutorMensaje autorMensaje(String id) {
        return autorMensajeRepo.findById(id)
                .orElseThrow(() -> new IllegalStateException("Falta seedear autor_mensaje." + id));
    }
}
