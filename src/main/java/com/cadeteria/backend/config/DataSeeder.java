package com.cadeteria.backend.config;

import com.cadeteria.backend.model.Admin;
import com.cadeteria.backend.model.AutorMensaje;
import com.cadeteria.backend.model.Configuracion;
import com.cadeteria.backend.model.EstadoCadete;
import com.cadeteria.backend.model.EstadoPedido;
import com.cadeteria.backend.model.ResultadoOferta;
import com.cadeteria.backend.model.TipoVehiculo;
import com.cadeteria.backend.repository.AdminRepository;
import com.cadeteria.backend.repository.AutorMensajeRepository;
import com.cadeteria.backend.repository.ConfiguracionRepository;
import com.cadeteria.backend.repository.EstadoCadeteRepository;
import com.cadeteria.backend.repository.EstadoPedidoRepository;
import com.cadeteria.backend.repository.ResultadoOfertaRepository;
import com.cadeteria.backend.repository.TipoVehiculoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Carga las parametrias base (spec/diseno sección 8: reemplazan los enums de estado/tipo)
 * la primera vez que arranca contra una base vacia. El id de cada fila es el codigo
 * estable que usa el resto del backend (ej. "LIBRE", "EN_CURSO") — el nombre es la
 * etiqueta editable desde el panel.
 */
@Component
@Order(1)
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final TipoVehiculoRepository tipoVehiculoRepo;
    private final EstadoCadeteRepository estadoCadeteRepo;
    private final EstadoPedidoRepository estadoPedidoRepo;
    private final ResultadoOfertaRepository resultadoOfertaRepo;
    private final AutorMensajeRepository autorMensajeRepo;
    private final AdminRepository adminRepo;
    private final ConfiguracionRepository configuracionRepo;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties props;

    public DataSeeder(TipoVehiculoRepository tipoVehiculoRepo,
                       EstadoCadeteRepository estadoCadeteRepo,
                       EstadoPedidoRepository estadoPedidoRepo,
                       ResultadoOfertaRepository resultadoOfertaRepo,
                       AutorMensajeRepository autorMensajeRepo,
                       AdminRepository adminRepo,
                       ConfiguracionRepository configuracionRepo,
                       PasswordEncoder passwordEncoder,
                       AppProperties props) {
        this.tipoVehiculoRepo = tipoVehiculoRepo;
        this.estadoCadeteRepo = estadoCadeteRepo;
        this.estadoPedidoRepo = estadoPedidoRepo;
        this.resultadoOfertaRepo = resultadoOfertaRepo;
        this.autorMensajeRepo = autorMensajeRepo;
        this.adminRepo = adminRepo;
        this.configuracionRepo = configuracionRepo;
        this.passwordEncoder = passwordEncoder;
        this.props = props;
    }

    @Override
    public void run(String... args) {
        seedTipoVehiculo();
        seedEstadoCadete();
        seedEstadoPedido();
        seedResultadoOferta();
        seedAutorMensaje();
        seedAdminInicial();
        backfillRolAdmins();
        seedConfiguracion();
    }

    private void seedAdminInicial() {
        if (adminRepo.count() > 0) return;
        Admin admin = new Admin();
        admin.setId(UUID.randomUUID().toString());
        admin.setUsername(props.getAdmin().getUsername());
        admin.setPasswordHash(passwordEncoder.encode(props.getAdmin().getPassword()));
        admin.setEnabled(true);
        admin.setRol("admin");
        adminRepo.save(admin);
        log.info("Seed: admin inicial '{}' creado.", admin.getUsername());
    }

    /**
     * Columna "rol" nueva (roles de admin, 2026-09-16) — en una base ya existente, los
     * admins cargados antes de este cambio quedan con NULL al agregar la columna
     * (`ddl-auto: update` no forzó un default). Sin este backfill, {@link Admin#isDueno()}
     * ya trata NULL como DUENO, pero conviene dejarlo explícito en la base.
     */
    private void backfillRolAdmins() {
        adminRepo.findAll().stream()
                .filter(a -> a.getRol() == null)
                .forEach(a -> {
                    a.setRol("admin");
                    adminRepo.save(a);
                });
    }

    /** Valores por defecto de diseno-tecnico.md sección 7 — editables despues desde el panel. */
    private void seedConfiguracion() {
        if (configuracionRepo.count() > 0) return;
        configuracionRepo.save(config("tiempo_limite_aceptacion_seg", "120"));
        configuracionRepo.save(config("frecuencia_ubicacion_seg", "45"));
        // Enriquecimiento pasivo de la cache de direcciones con el GPS de los cadetes (spec-geocoding-cache.md
        // §12) — 0 = apagado. Default de producción: 1200 seg (20 min), para una flota de decenas de
        // cadetes reales; bajarlo (ej. 30 seg) solo tiene sentido probando con pocas cuentas a la vez
        // (ver MapeoCallesCadetesService).
        configuracionRepo.save(config("mapeo_calles_cadetes_intervalo_seg", "1200"));
        // Numero correlativo de pedidos, para mostrar en vez del UUID interno.
        configuracionRepo.save(config("proximo_numero_pedido", "1500000"));
        // Apagada por defecto: hasta que el admin la prenda a mano, asignar sigue siendo
        // 100% manual (boton "Asignar" del dashboard, con sugerencia del sistema).
        configuracionRepo.save(config("asignacion_automatica", "false"));
        // Reintentos de asignacion (PedidoService.buscarCandidato): despues de este numero
        // de rechazos explicitos de un mismo cadete para un mismo pedido, se lo deja de
        // ofertar — salvo que el pedido ya lleve "minutos_pedido_urgente_reintentar" sin
        // poder asignarse, en cuyo caso se ignora el limite para que no quede flotando.
        configuracionRepo.save(config("max_rechazos_por_pedido", "3"));
        configuracionRepo.save(config("minutos_pedido_urgente_reintentar", "30"));
        // Tope de viajes sin terminar que la asignacion automatica/sugerida le puede dar a
        // UN cadete, independiente de su maxViajesSimultaneos individual (que es el techo
        // que el cadete puede cargar, no cuanto quiere darle el admin de una sola vez).
        configuracionRepo.save(config("asignacion_automatica_max_viajes_cadete", "1"));
        // Apagado por defecto: si se prende, entre cadetes libres igualmente disponibles se
        // prioriza al que historicamente rechaza menos ofertas por sobre el orden FIFO puro.
        configuracionRepo.save(config("asignacion_prioriza_ranking_aceptacion", "false"));
        // Umbrales de los avisos de demora al cadete (PedidoService.avisosDemora).
        configuracionRepo.save(config("alerta_demora_retiro_min", "30"));
        configuracionRepo.save(config("alerta_demora_finalizacion_min", "60"));
        // Cloudinary (subida de imagenes desde el front, igual que en el ecommerce): unsigned
        // upload preset, no son datos secretos. Vacio hasta que se cargen desde el panel.
        configuracionRepo.save(config("cloudinary_cloud_name", ""));
        configuracionRepo.save(config("cloudinary_upload_preset", ""));
        // API keys de ruteo por calle (RutaService) — varian por cliente, se cargan desde el
        // panel. Vacias por defecto: sin ellas, la cotizacion por km y la ruta al cadete
        // siguen funcionando con OSRM (gratis, sin key) o linea recta como ultimo respaldo.
        configuracionRepo.save(config("open_route_service_key", ""));
        configuracionRepo.save(config("open_route_service_url", ""));
        configuracionRepo.save(config("graphhopper_key", ""));
        // Cotizacion automatica (CotizacionService) — valores de ejemplo para que la demo
        // (DemoPedidoSeeder) ya sugiera precio en "Nuevo pedido" sin que el admin tenga que
        // cargar nada a mano primero. AUTOMATICO = zona si tiene tarifa, si no por km.
        configuracionRepo.save(config("metodo_cotizacion", "AUTOMATICO"));
        configuracionRepo.save(config("precio_base_viaje", "2000"));
        configuracionRepo.save(config("distancia_minima_km", "2"));
        configuracionRepo.save(config("precio_por_km", "150"));
        configuracionRepo.save(config("recargo_dinero_transportado_umbral", "10000"));
        configuracionRepo.save(config("recargo_dinero_transportado_monto", "100"));
        // Bloqueo temporal de cuenta tras intentos fallidos de login (ronda 6, punto 49).
        configuracionRepo.save(config("max_intentos_login", "5"));
        configuracionRepo.save(config("bloqueo_login_min", "15"));
        // Modelo de cobro del cadete (ronda 7): cuota semanal fija y comisión % con crédito.
        configuracionRepo.save(config("pago_semanal_monto", "5000"));
        configuracionRepo.save(config("comision_porcentaje", "10"));
        configuracionRepo.save(config("credito_bajo_alerta_umbral", "500"));
        log.info("Seed: configuracion cargada.");
    }

    private Configuracion config(String clave, String valor) {
        Configuracion c = new Configuracion();
        c.setClave(clave);
        c.setValor(valor);
        return c;
    }

    private void seedTipoVehiculo() {
        if (tipoVehiculoRepo.count() > 0) return;
        tipoVehiculoRepo.save(lookup(new TipoVehiculo(), "MOTO", "Moto"));
        tipoVehiculoRepo.save(lookup(new TipoVehiculo(), "BICI", "Bici"));
        log.info("Seed: tipo_vehiculo cargado.");
    }

    private void seedEstadoCadete() {
        if (estadoCadeteRepo.count() > 0) return;
        estadoCadeteRepo.save(lookup(new EstadoCadete(), "LIBRE", "Libre"));
        estadoCadeteRepo.save(lookup(new EstadoCadete(), "OCUPADO", "Ocupado"));
        estadoCadeteRepo.save(lookup(new EstadoCadete(), "DESCONECTADO", "Desconectado"));
        log.info("Seed: estado_cadete cargado.");
    }

    private void seedEstadoPedido() {
        if (estadoPedidoRepo.count() == 0) {
            estadoPedidoRepo.save(lookup(new EstadoPedido(), "PROGRAMADO", "Programado"));
            estadoPedidoRepo.save(lookup(new EstadoPedido(), "SIN_ASIGNAR", "Sin asignación"));
            estadoPedidoRepo.save(lookup(new EstadoPedido(), "PENDIENTE", "Pendiente"));
            estadoPedidoRepo.save(lookup(new EstadoPedido(), "EN_CURSO", "En curso"));
            estadoPedidoRepo.save(lookup(new EstadoPedido(), "FINALIZADO", "Finalizado"));
            estadoPedidoRepo.save(lookup(new EstadoPedido(), "CANCELADO", "Cancelado"));
            log.info("Seed: estado_pedido cargado.");
        }
        // Se agrega por fuera del "count == 0" de arriba para que una base ya seedeada
        // (instalaciones existentes) también reciba el estado nuevo al actualizar (ronda 3, punto 56).
        if (estadoPedidoRepo.findById("NO_ENTREGADO").isEmpty()) {
            estadoPedidoRepo.save(lookup(new EstadoPedido(), "NO_ENTREGADO", "No se pudo entregar"));
            log.info("Seed: estado_pedido NO_ENTREGADO agregado.");
        }
    }

    private void seedResultadoOferta() {
        if (resultadoOfertaRepo.count() > 0) return;
        resultadoOfertaRepo.save(lookup(new ResultadoOferta(), "PENDIENTE", "Pendiente"));
        resultadoOfertaRepo.save(lookup(new ResultadoOferta(), "ACEPTADO", "Aceptado"));
        resultadoOfertaRepo.save(lookup(new ResultadoOferta(), "RECHAZADO", "Rechazado"));
        resultadoOfertaRepo.save(lookup(new ResultadoOferta(), "EXPIRADO", "Expirado"));
        resultadoOfertaRepo.save(lookup(new ResultadoOferta(), "QUITADO_ADMIN", "Quitado por el admin"));
        log.info("Seed: resultado_oferta cargado.");
    }

    private void seedAutorMensaje() {
        if (autorMensajeRepo.count() > 0) return;
        autorMensajeRepo.save(lookup(new AutorMensaje(), "ADMIN", "Admin"));
        autorMensajeRepo.save(lookup(new AutorMensaje(), "CADETE", "Cadete"));
        log.info("Seed: autor_mensaje cargado.");
    }

    private static <T extends com.cadeteria.backend.model.Lookup> T lookup(T entity, String id, String nombre) {
        entity.setId(id);
        entity.setNombre(nombre);
        return entity;
    }
}
