package com.cadeteria.backend.service;

import com.cadeteria.backend.common.BadRequestException;
import com.cadeteria.backend.common.ResourceNotFoundException;
import com.cadeteria.backend.dto.PedidoDtos.FinalizarRequest;
import com.cadeteria.backend.dto.PedidoDtos.PedidoRequest;
import com.cadeteria.backend.dto.PedidoDtos.PedidoResponse;
import com.cadeteria.backend.model.Cadete;
import com.cadeteria.backend.model.EstadoPedido;
import com.cadeteria.backend.model.OfertaPedido;
import com.cadeteria.backend.model.Pedido;
import com.cadeteria.backend.model.PedidoComentario;
import com.cadeteria.backend.model.PedidoPrecioLog;
import com.cadeteria.backend.model.PedidoParada;
import com.cadeteria.backend.model.PedidoUbicacion;
import com.cadeteria.backend.model.ResultadoOferta;
import com.cadeteria.backend.model.TipoVehiculo;
import com.cadeteria.backend.model.Zona;
import com.cadeteria.backend.config.AppProperties;
import com.cadeteria.backend.repository.CadeteRepository;
import com.cadeteria.backend.repository.EstadoCadeteRepository;
import com.cadeteria.backend.repository.IncidenciaRepository;
import com.cadeteria.backend.repository.MovimientoCreditoRepository;
import com.cadeteria.backend.repository.EstadoPedidoRepository;
import com.cadeteria.backend.repository.OfertaPedidoRepository;
import com.cadeteria.backend.repository.PedidoRepository;
import com.cadeteria.backend.repository.PedidoComentarioRepository;
import com.cadeteria.backend.repository.PedidoPrecioLogRepository;
import com.cadeteria.backend.repository.PedidoParadaRepository;
import com.cadeteria.backend.repository.PedidoUbicacionRepository;
import com.cadeteria.backend.repository.ResultadoOfertaRepository;
import com.cadeteria.backend.repository.TipoVehiculoRepository;
import com.cadeteria.backend.repository.ZonaRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Nucleo del sistema: alta de pedidos, sugerencia y asignacion por zona (spec 5.1),
 * el ciclo de ofertar/aceptar/rechazar/expirar con reasignacion automatica en cadena
 * (spec sección 4 — el fix del bug de origen), y el cierre del viaje.
 */
@Service
@Transactional
public class PedidoService {

    private static final List<String> ESTADOS_ACTIVOS = List.of("SIN_ASIGNAR", "PENDIENTE", "EN_CURSO", "NO_ENTREGADO");
    private static final List<String> ESTADOS_FINALES = List.of("FINALIZADO", "CANCELADO");
    private static final List<String> ESTADOS_OCUPAN_CADETE = List.of("PENDIENTE", "EN_CURSO");
    private static final ZoneId ZONA_ART = ZoneId.of("America/Argentina/Buenos_Aires");

    private final PedidoRepository repo;
    private final CadeteRepository cadeteRepo;
    private final ZonaRepository zonaRepo;
    private final TipoVehiculoRepository tipoVehiculoRepo;
    private final EstadoPedidoRepository estadoPedidoRepo;
    private final ResultadoOfertaRepository resultadoOfertaRepo;
    private final OfertaPedidoRepository ofertaRepo;
    private final EstadoCadeteRepository estadoCadeteRepo;
    private final ConfiguracionService configuracionService;
    private final WebSocketPublisher publisher;
    private final FcmService fcmService;
    private final SmsGatewayService smsGatewayService;
    private final PedidoUbicacionRepository pedidoUbicacionRepo;
    private final PedidoComentarioRepository pedidoComentarioRepo;
    private final PedidoPrecioLogRepository pedidoPrecioLogRepo;
    private final WebPushService webPushService;
    private final PedidoParadaRepository pedidoParadaRepo;
    private final MovimientoCreditoRepository movimientoCreditoRepo;
    private final IncidenciaRepository incidenciaRepo;
    private final String frontBaseUrlSeguimiento;

    public PedidoService(PedidoRepository repo, CadeteRepository cadeteRepo, ZonaRepository zonaRepo,
                          TipoVehiculoRepository tipoVehiculoRepo, EstadoPedidoRepository estadoPedidoRepo,
                          ResultadoOfertaRepository resultadoOfertaRepo, OfertaPedidoRepository ofertaRepo,
                          EstadoCadeteRepository estadoCadeteRepo, ConfiguracionService configuracionService,
                          WebSocketPublisher publisher, FcmService fcmService, SmsGatewayService smsGatewayService,
                          PedidoUbicacionRepository pedidoUbicacionRepo, PedidoComentarioRepository pedidoComentarioRepo,
                          PedidoPrecioLogRepository pedidoPrecioLogRepo, WebPushService webPushService,
                          PedidoParadaRepository pedidoParadaRepo, MovimientoCreditoRepository movimientoCreditoRepo,
                          IncidenciaRepository incidenciaRepo, AppProperties props) {
        this.repo = repo;
        this.movimientoCreditoRepo = movimientoCreditoRepo;
        this.incidenciaRepo = incidenciaRepo;
        this.cadeteRepo = cadeteRepo;
        this.zonaRepo = zonaRepo;
        this.tipoVehiculoRepo = tipoVehiculoRepo;
        this.estadoPedidoRepo = estadoPedidoRepo;
        this.resultadoOfertaRepo = resultadoOfertaRepo;
        this.ofertaRepo = ofertaRepo;
        this.estadoCadeteRepo = estadoCadeteRepo;
        this.configuracionService = configuracionService;
        this.publisher = publisher;
        this.fcmService = fcmService;
        this.smsGatewayService = smsGatewayService;
        this.pedidoParadaRepo = pedidoParadaRepo;
        this.pedidoUbicacionRepo = pedidoUbicacionRepo;
        this.pedidoComentarioRepo = pedidoComentarioRepo;
        this.pedidoPrecioLogRepo = pedidoPrecioLogRepo;
        this.webPushService = webPushService;
        this.frontBaseUrlSeguimiento = props.getFrontBaseUrl();
    }

    /** Trayecto GPS real guardado mientras el pedido estaba EN_CURSO (para el mapa del detalle en el panel). */
    @Transactional(readOnly = true)
    public List<PedidoUbicacion> trayectoDe(String pedidoId) {
        return pedidoUbicacionRepo.findByPedidoIdOrderByCapturadoEnAsc(pedidoId);
    }

    /** Comentarios que fue dejando el cadete sobre el pedido — para la pestaña "Comentarios" del detalle. */
    @Transactional(readOnly = true)
    public List<PedidoComentario> comentariosDe(String pedidoId) {
        return pedidoComentarioRepo.findByPedidoIdOrderByCreadoEnAsc(pedidoId);
    }

    /** El cadete deja una nota de texto libre sobre su propio viaje (ej. "entregado en porteria a Fulano"). */
    public PedidoComentario agregarComentario(String pedidoId, String cadeteUsername, String texto) {
        if (texto == null || texto.isBlank()) {
            throw new BadRequestException("El comentario no puede estar vacio.");
        }
        Pedido pedido = get(pedidoId);
        Cadete cadete = cadeteDelUsername(cadeteUsername);
        validarPertenencia(pedido, cadete);
        PedidoComentario comentario = new PedidoComentario();
        comentario.setId(UUID.randomUUID().toString());
        comentario.setPedido(pedido);
        comentario.setCadete(cadete);
        comentario.setTexto(texto.trim());
        pedidoComentarioRepo.save(comentario);
        return comentario;
    }

    /** Mejora 101 — antes el admin solo podía leer los comentarios del cadete, no dejar los propios. */
    public PedidoComentario agregarComentarioAdmin(String pedidoId, String adminUsername, String texto) {
        if (texto == null || texto.isBlank()) {
            throw new BadRequestException("El comentario no puede estar vacio.");
        }
        Pedido pedido = get(pedidoId);
        PedidoComentario comentario = new PedidoComentario();
        comentario.setId(UUID.randomUUID().toString());
        comentario.setPedido(pedido);
        comentario.setAdminUsername(adminUsername);
        comentario.setTexto(texto.trim());
        pedidoComentarioRepo.save(comentario);
        return comentario;
    }

    /** Historial de cambios de precio de un pedido (mejora 75) — para ver quién y cuándo lo tocó. */
    @Transactional(readOnly = true)
    public List<PedidoPrecioLog> historialPreciosDe(String pedidoId) {
        return pedidoPrecioLogRepo.findByPedidoIdOrderByCambiadoEnDesc(pedidoId);
    }

    /** El admin edita el precio de un pedido ya cargado (antes no se podía tocar después de crearlo). */
    public Pedido editarPrecio(String pedidoId, java.math.BigDecimal nuevoPrecio, String adminUsername) {
        if (nuevoPrecio == null || nuevoPrecio.signum() < 0) {
            throw new BadRequestException("El precio tiene que ser un numero valido.");
        }
        Pedido pedido = get(pedidoId);
        if (ESTADOS_FINALES.contains(pedido.getEstado().getId())) {
            throw new BadRequestException("No se puede editar el precio de un pedido finalizado o cancelado.");
        }
        if (pedido.getPrecio().compareTo(nuevoPrecio) == 0) {
            return pedido;
        }
        PedidoPrecioLog log = new PedidoPrecioLog();
        log.setId(UUID.randomUUID().toString());
        log.setPedido(pedido);
        log.setPrecioAnterior(pedido.getPrecio());
        log.setPrecioNuevo(nuevoPrecio);
        log.setCambiadoPorUsername(adminUsername);
        pedidoPrecioLogRepo.save(log);
        pedido.setPrecio(nuevoPrecio);
        return repo.save(pedido);
    }

    /** Mejora 93 — marcar/desmarcar un pedido como prioritario/urgente, solo visual en el dashboard. */
    public Pedido setPrioritario(String pedidoId, boolean prioritario) {
        Pedido pedido = get(pedidoId);
        pedido.setPrioritario(prioritario);
        return repo.save(pedido);
    }

    @Transactional(readOnly = true)
    public Pedido get(String id) {
        return repo.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Pedido", id));
    }

    /**
     * Autocompletar nombre por teléfono al cargar un pedido (spec 5.6): no hay entidad
     * "cliente" — solo se busca el nombre del pedido más reciente con ese teléfono, a
     * modo de sugerencia. El admin la puede editar libremente para este pedido puntual
     * sin que eso modifique nada de lo ya guardado (no hay dónde guardarlo: el teléfono
     * nunca fuerza un único nombre).
     */
    @Transactional(readOnly = true)
    public Optional<String> nombreClientePorTelefono(String telefono) {
        return repo.findFirstByClienteTelefonoOrderByCreadoEnDesc(telefono).map(Pedido::getClienteNombre);
    }

    @Transactional(readOnly = true)
    public Pedido getPorToken(String token) {
        return repo.findByTokenSeguimiento(token)
                .orElseThrow(() -> ResourceNotFoundException.of("Pedido", token));
    }

    /** Mejora 89 — el cliente se suscribe a Web Push desde su propia página de seguimiento. */
    public void suscribirPush(String token, String endpoint, String p256dh, String auth) {
        webPushService.suscribir(getPorToken(token), endpoint, p256dh, auth);
    }

    @Transactional(readOnly = true)
    public List<Pedido> listarActivos() {
        return repo.findByEstadoIdInOrderByCreadoEnDesc(ESTADOS_ACTIVOS);
    }

    /** Para el ícono de alertas centralizado del panel (ronda 4, punto 18). */
    @Transactional(readOnly = true)
    public long contarSmsFallidos() {
        return repo.countByEstadoIdInAndSmsFallidoTrue(ESTADOS_ACTIVOS);
    }

    @Transactional(readOnly = true)
    public List<Pedido> listarProgramados() {
        return repo.findByEstadoIdInOrderByCreadoEnDesc(List.of("PROGRAMADO"));
    }

    @Transactional(readOnly = true)
    public List<Pedido> listarFinalizados() {
        return repo.findByEstadoIdInOrderByCreadoEnDesc(ESTADOS_FINALES);
    }

    /** El viaje que el cadete tiene asignado ahora mismo (pantalla principal de la app), si hay. */
    @Transactional(readOnly = true)
    public Optional<Pedido> activoDe(String cadeteUsername) {
        Cadete cadete = cadeteDelUsername(cadeteUsername);
        return repo.findByCadeteAsignadoIdAndEstadoIdIn(cadete.getId(), ESTADOS_OCUPAN_CADETE)
                .stream().findFirst();
    }

    /**
     * Todos los viajes asignados/en curso del cadete — a diferencia de {@link #activoDe},
     * no asume uno solo (spec 5.3: un cadete puede tener varios simultáneos si
     * max_viajes_simultaneos lo permite). Sección "Asignados y en curso" de la app.
     */
    @Transactional(readOnly = true)
    public List<Pedido> activosDe(String cadeteUsername) {
        Cadete cadete = cadeteDelUsername(cadeteUsername);
        return repo.findByCadeteAsignadoIdAndEstadoIdIn(cadete.getId(), ESTADOS_OCUPAN_CADETE);
    }

    /** Detalle de un pedido puntual del cadete (incluye finalizados) — sección "Finalizados" de la app. */
    @Transactional(readOnly = true)
    public Pedido getDeCadete(String pedidoId, String cadeteUsername) {
        Pedido pedido = get(pedidoId);
        Cadete cadete = cadeteDelUsername(cadeteUsername);
        validarPertenencia(pedido, cadete);
        return pedido;
    }

    /**
     * Resumen para la sección "Finalizados" de la app: el listado de viajes finalizados
     * y cuántas ofertas rechazó o dejó vencer (se le reasignaron a otro cadete, ver
     * sección 4 — la tabla oferta_pedido es la única fuente de esto, porque
     * pedido.cadete_asignado_id termina apuntando al cadete que sí lo completó).
     * desde/hasta null = todo el historial (comportamiento de siempre); con rango, filtra
     * por finalizadoEn/ofrecidoEn en [desde, hasta) — auditoría UX 2026-09-13: antes la
     * app siempre traía todo pero el título decía "Resumen de hoy", así que el cadete no
     * podía chequear días anteriores contra la liquidación semanal.
     */
    @Transactional(readOnly = true)
    public HistorialCadete historialDe(String cadeteUsername, Instant desde, Instant hasta) {
        Cadete cadete = cadeteDelUsername(cadeteUsername);
        List<Pedido> finalizados = desde == null
                ? repo.findByCadeteAsignadoIdAndEstadoIdOrderByFinalizadoEnDesc(cadete.getId(), "FINALIZADO")
                : repo.findByCadeteAsignadoIdAndEstadoIdAndFinalizadoEnBetweenOrderByFinalizadoEnDesc(
                        cadete.getId(), "FINALIZADO", desde, hasta);
        long rechazados = desde == null
                ? ofertaRepo.countByCadeteIdAndResultadoId(cadete.getId(), "RECHAZADO")
                : ofertaRepo.countByCadeteIdAndResultadoIdAndOfrecidoEnBetween(cadete.getId(), "RECHAZADO", desde, hasta);
        long noAceptados = desde == null
                ? ofertaRepo.countByCadeteIdAndResultadoId(cadete.getId(), "EXPIRADO")
                : ofertaRepo.countByCadeteIdAndResultadoIdAndOfrecidoEnBetween(cadete.getId(), "EXPIRADO", desde, hasta);
        return new HistorialCadete(finalizados, rechazados, noAceptados);
    }

    public record HistorialCadete(List<Pedido> finalizados, long cantidadRechazados, long cantidadNoAceptados) {}

    // --- Alta ---

    public Pedido crear(PedidoRequest req) {
        Zona zona = zonaRepo.findById(req.zonaId())
                .orElseThrow(() -> ResourceNotFoundException.of("Zona", req.zonaId()));
        TipoVehiculo tipo = tipoVehiculoRepo.findById(req.tipoVehiculoRequeridoId())
                .orElseThrow(() -> ResourceNotFoundException.of("Tipo de vehiculo", req.tipoVehiculoRequeridoId()));

        boolean esProgramado = req.programado() && req.fechaProgramada() != null
                && req.fechaProgramada().isAfter(Instant.now());

        Pedido p = new Pedido();
        p.setId(UUID.randomUUID().toString());
        p.setNumero(configuracionService.siguienteNumeroPedido());
        p.setTokenSeguimiento(UUID.randomUUID().toString());
        p.setClienteTelefono(req.clienteTelefono().trim());
        p.setClienteNombre(req.clienteNombre().trim());
        p.setOrigenDireccion(req.origenDireccion().trim());
        p.setOrigenLat(req.origenLat());
        p.setOrigenLng(req.origenLng());
        p.setDestinoDireccion(req.destinoDireccion().trim());
        p.setDestinoLat(req.destinoLat());
        p.setDestinoLng(req.destinoLng());
        p.setPrecio(req.precio());
        p.setMontoDeclarado(req.montoDeclarado() == null ? BigDecimal.ZERO : req.montoDeclarado());
        p.setDetalle(req.detalle());
        p.setZona(zona);
        p.setTipoVehiculoRequerido(tipo);
        p.setProgramado(esProgramado);
        p.setFechaProgramada(esProgramado ? req.fechaProgramada() : null);
        p.setEstado(estado(esProgramado ? "PROGRAMADO" : "SIN_ASIGNAR"));

        p = repo.save(p);

        if (req.paradasAdicionales() != null) {
            int orden = 1;
            for (var pr : req.paradasAdicionales()) {
                PedidoParada parada = new PedidoParada();
                parada.setId(UUID.randomUUID().toString());
                parada.setPedido(p);
                parada.setOrden(orden++);
                parada.setDireccion(pr.direccion().trim());
                parada.setLat(pr.lat());
                parada.setLng(pr.lng());
                pedidoParadaRepo.save(parada);
                p.getParadas().add(parada);
            }
        }

        PedidoResponse dto = PedidoResponse.from(p);
        publisher.publicarPedido(dto);
        if (!esProgramado) {
            publisher.publicarAlertaPedidoNuevo(dto);
        }
        return p;
    }

    /**
     * Mejora 87 — el cliente repite su pedido desde la página de seguimiento, sin llamar.
     * Distinto de "duplicar" (mejora 72, que dispara el admin): acá crea un pedido nuevo con
     * los mismos datos (mismo precio de referencia, el admin lo puede ajustar como cualquier
     * pedido), SIN_ASIGNAR, listo para que el admin lo revise igual que cualquier pedido nuevo.
     */
    public Pedido repetirPorToken(String token) {
        Pedido original = getPorToken(token);
        if (!"FINALIZADO".equals(original.getEstado().getId())) {
            throw new BadRequestException("Solo se puede repetir un pedido ya finalizado.");
        }
        PedidoRequest req = new PedidoRequest(
                original.getClienteTelefono(), original.getClienteNombre(),
                original.getOrigenDireccion(), original.getOrigenLat(), original.getOrigenLng(),
                original.getDestinoDireccion(), original.getDestinoLat(), original.getDestinoLng(),
                original.getPrecio(), null, "Repetición del pedido #" + original.getNumero(),
                original.getZona().getId(), original.getTipoVehiculoRequerido().getId(),
                false, null, null);
        return crear(req);
    }

    /** Candidato sugerido para asignacion (spec 5.1) — el admin lo confirma con si/no antes de ofertarlo. */
    @Transactional(readOnly = true)
    public Optional<Cadete> sugerirCandidato(String pedidoId) {
        Pedido pedido = get(pedidoId);
        Set<String> yaOfertados = ofertaRepo.findByPedidoId(pedidoId).stream()
                .map(o -> o.getCadete().getId())
                .collect(Collectors.toSet());
        return buscarCandidato(pedido, yaOfertados);
    }

    /**
     * Cadetes que HOY podrían recibir este pedido sin que el backend lo rechace (mismos
     * chequeos que hace {@link #asignar} al confirmar: activo, LIBRE, pago semanal/crédito
     * al día, dentro de sus topes). A propósito NO filtra por zona/vehículo/turno — eso es
     * a criterio del admin, "asignar" tampoco lo exige. Para el desplegable de "Asignar" en
     * el dashboard, así no aparecen opciones que van a tirar error seguro al confirmar.
     */
    @Transactional(readOnly = true)
    public List<Cadete> candidatosValidos(String pedidoId) {
        Pedido pedido = get(pedidoId);
        return cadeteRepo.findAll().stream()
                .filter(Cadete::isActivo)
                .filter(c -> "LIBRE".equals(c.getEstado().getId()))
                .filter(this::puedeRecibirViajes)
                .filter(c -> dentroDeTopes(c, pedido))
                .toList();
    }

    private Optional<Cadete> buscarCandidato(Pedido pedido, Set<String> excluirCadeteIds) {
        Set<String> zonasCompatibles = zonasCompatibles(pedido.getZona());
        return cadeteRepo.findAll().stream()
                .filter(Cadete::isActivo)
                .filter(c -> "LIBRE".equals(c.getEstado().getId()))
                .filter(this::puedeRecibirViajes)
                .filter(c -> !excluirCadeteIds.contains(c.getId()))
                .filter(c -> c.getTipoVehiculo().getId().equals(pedido.getTipoVehiculoRequerido().getId()))
                .filter(c -> c.getZonaActual() != null && zonasCompatibles.contains(c.getZonaActual().getId()))
                .filter(c -> dentroDeTopes(c, pedido))
                .filter(this::dentroDeTurno)
                .filter(c -> !incidenciaRepo.existsByCadeteIdAndPrioridadAndEstado(c.getId(), "GRAVE", "ABIERTA"))
                .min((a, b) -> a.getOrdenColaEspera().compareTo(b.getOrdenColaEspera()));
    }

    /**
     * true si el cadete no tiene turno fijo cargado (siempre disponible, comportamiento
     * previo) o si la hora actual (America/Argentina/Buenos_Aires) cae dentro de su
     * turno — soporta turnos que cruzan medianoche (ej. 22:00 a 06:00).
     */
    private boolean dentroDeTurno(Cadete c) {
        LocalTime inicio = c.getTurnoInicio();
        LocalTime fin = c.getTurnoFin();
        if (inicio == null || fin == null) return true;
        LocalTime ahora = LocalTime.now(ZONA_ART);
        if (inicio.equals(fin)) return true;
        if (inicio.isBefore(fin)) {
            return !ahora.isBefore(inicio) && ahora.isBefore(fin);
        }
        return !ahora.isBefore(inicio) || ahora.isBefore(fin);
    }

    private Set<String> zonasCompatibles(Zona zona) {
        Set<String> ids = zona.getZonasAledanas().stream().map(Zona::getId).collect(Collectors.toSet());
        ids.add(zona.getId());
        return ids;
    }

    private boolean dentroDeTopes(Cadete cadete, Pedido nuevoPedido) {
        List<Pedido> activos = repo.findByCadeteAsignadoIdAndEstadoIdIn(cadete.getId(), ESTADOS_OCUPAN_CADETE);
        if (cadete.getMaxViajesSimultaneos() != null && activos.size() >= cadete.getMaxViajesSimultaneos()) {
            return false;
        }
        if (cadete.getMontoMaximoTransportado() != null) {
            BigDecimal comprometido = activos.stream()
                    .map(Pedido::getMontoDeclarado)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (comprometido.add(nuevoPedido.getMontoDeclarado()).compareTo(cadete.getMontoMaximoTransportado()) > 0) {
                return false;
            }
        }
        if (cadete.getMaxViajesDiarios() != null || cadete.getMaxViajesSemanales() != null) {
            Instant ahora = Instant.now();
            if (cadete.getMaxViajesDiarios() != null) {
                Instant inicioDia = LocalDate.now(ZONA_ART).atStartOfDay(ZONA_ART).toInstant();
                long hoy = repo.findByCadeteAsignadoIdAndEstadoIdAndFinalizadoEnBetweenOrderByFinalizadoEnDesc(
                        cadete.getId(), "FINALIZADO", inicioDia, ahora).size();
                if (hoy >= cadete.getMaxViajesDiarios()) return false;
            }
            if (cadete.getMaxViajesSemanales() != null) {
                Instant inicioSemana = LocalDate.now(ZONA_ART).with(DayOfWeek.MONDAY).atStartOfDay(ZONA_ART).toInstant();
                long semana = repo.findByCadeteAsignadoIdAndEstadoIdAndFinalizadoEnBetweenOrderByFinalizadoEnDesc(
                        cadete.getId(), "FINALIZADO", inicioSemana, ahora).size();
                if (semana >= cadete.getMaxViajesSemanales()) return false;
            }
        }
        if ("PORCENTAJE".equals(cadete.getModalidadPago()) && cadete.getCreditoDisponible().compareTo(comisionDe(nuevoPedido)) < 0) {
            return false;
        }
        return true;
    }

    /** false si el cadete es SEMANAL y todavía no lo habilitó el admin esta semana (ronda 7). */
    private boolean puedeRecibirViajes(Cadete cadete) {
        return !"SEMANAL".equals(cadete.getModalidadPago()) || cadete.isHabilitadoPago();
    }

    /** Comisión que se le descuenta al cadete PORCENTAJE al aceptar este pedido (ronda 7). */
    private BigDecimal comisionDe(Pedido pedido) {
        BigDecimal pct = configuracionService.getBigDecimal("comision_porcentaje", BigDecimal.TEN);
        return pedido.getPrecio().multiply(pct).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    /**
     * Devuelve al crédito del cadete la comisión ya descontada si el viaje se destraba sin
     * terminarse (Anular, Quitar, Reasignar, No se pudo entregar) — solo se cobra por lo
     * que efectivamente se concreta (ronda 7).
     */
    private void reembolsarComisionSiCorresponde(Pedido pedido, Cadete cadete) {
        if (cadete == null || pedido.getComisionDescontada() == null) return;
        BigDecimal comision = pedido.getComisionDescontada();
        cadete.setCreditoDisponible(cadete.getCreditoDisponible().add(comision));
        pedido.setComisionDescontada(null);
        cadeteRepo.save(cadete);
        registrarMovimientoCredito(cadete, "REEMBOLSO", comision, pedido.getId(), pedido.getNumero());
    }

    /** Historial de movimientos de crédito (ronda 10, punto 94) — ACREDITACION/COMISION vienen del admin/CadeteService, REEMBOLSO de acá. */
    private void registrarMovimientoCredito(Cadete cadete, String tipo, BigDecimal monto, String pedidoId, Long pedidoNumero) {
        com.cadeteria.backend.model.MovimientoCredito m = new com.cadeteria.backend.model.MovimientoCredito();
        m.setId(java.util.UUID.randomUUID().toString());
        m.setCadeteId(cadete.getId());
        m.setTipo(tipo);
        m.setMonto(monto);
        m.setSaldoResultante(cadete.getCreditoDisponible());
        m.setPedidoId(pedidoId);
        m.setPedidoNumero(pedidoNumero);
        movimientoCreditoRepo.save(m);
    }

    /** Avisa por push al cadete cuando su crédito cae por debajo del umbral configurado — una sola vez (ronda 7). */
    private void avisarCreditoBajoSiCorresponde(Cadete cadete) {
        BigDecimal umbral = configuracionService.getBigDecimal("credito_bajo_alerta_umbral", BigDecimal.valueOf(500));
        boolean bajo = cadete.getCreditoDisponible().compareTo(umbral) < 0;
        if (bajo && !cadete.isAlertaCreditoBajoEnviada()) {
            cadete.setAlertaCreditoBajoEnviada(true);
            cadeteRepo.save(cadete);
            fcmService.enviar(cadete.getFcmToken(), "Crédito bajo",
                    "Te queda poco crédito ($" + cadete.getCreditoDisponible() + "). Cargá más para seguir recibiendo viajes.",
                    Map.of("tipo", "CREDITO_BAJO"));
        } else if (!bajo && cadete.isAlertaCreditoBajoEnviada()) {
            cadete.setAlertaCreditoBajoEnviada(false);
            cadeteRepo.save(cadete);
        }
    }

    // --- Asignacion (admin) ---

    /** El admin confirma un candidato (sugerido o elegido a mano) — dispara la primera oferta. */
    public Pedido asignar(String pedidoId, String cadeteId, String adminUsername) {
        Pedido pedido = get(pedidoId);
        if (!"SIN_ASIGNAR".equals(pedido.getEstado().getId())) {
            throw new BadRequestException("El pedido ya tiene una asignacion en curso.");
        }
        Cadete cadete = cadeteRepo.findById(cadeteId)
                .orElseThrow(() -> ResourceNotFoundException.of("Cadete", cadeteId));
        if (!cadete.isActivo() || !"LIBRE".equals(cadete.getEstado().getId())) {
            throw new BadRequestException("El cadete no esta libre.");
        }
        if (!puedeRecibirViajes(cadete)) {
            throw new BadRequestException("El cadete todavia no pago la cuota semanal — no se le puede asignar.");
        }
        if (!dentroDeTopes(cadete, pedido)) {
            throw new BadRequestException("El cadete supera su tope de viajes, de dinero transportado o no le alcanza el crédito.");
        }
        pedido.setAsignadoPorUsername(adminUsername);
        ofertar(pedido, cadete);
        return pedido;
    }

    /**
     * Agrupar pedidos de la misma zona (o zonas aledañas) en una sola oferta a un cadete
     * (ronda 4, punto 61) — para repartos que van al mismo lado, en vez de asignarlos uno
     * por uno. Reusa `ofertar()` pedido por pedido (el cadete sigue viendo/aceptando cada
     * uno por separado en la app, sin ningún cambio ahí); requiere que el cadete tenga
     * "Máx. viajes simultáneos" configurado en 2 o más si el lote tiene más de un pedido.
     */
    public List<Pedido> asignarLote(List<String> pedidoIds, String cadeteId, String adminUsername) {
        if (pedidoIds == null || pedidoIds.isEmpty()) {
            throw new BadRequestException("Elegí al menos un pedido.");
        }
        Cadete cadete = cadeteRepo.findById(cadeteId)
                .orElseThrow(() -> ResourceNotFoundException.of("Cadete", cadeteId));
        if (!cadete.isActivo() || !"LIBRE".equals(cadete.getEstado().getId())) {
            throw new BadRequestException("El cadete no esta libre.");
        }
        if (!puedeRecibirViajes(cadete)) {
            throw new BadRequestException("El cadete todavia no pago la cuota semanal — no se le puede asignar.");
        }
        List<Pedido> pedidos = pedidoIds.stream().map(this::get).toList();
        Set<String> zonasCompatibles = zonasCompatibles(pedidos.get(0).getZona());
        for (Pedido p : pedidos) {
            if (!"SIN_ASIGNAR".equals(p.getEstado().getId())) {
                throw new BadRequestException("El pedido #" + p.getNumero() + " ya tiene una asignacion en curso.");
            }
            if (!zonasCompatibles.contains(p.getZona().getId())) {
                throw new BadRequestException("Todos los pedidos del lote tienen que ser de la misma zona (o zonas aledañas).");
            }
        }
        for (Pedido p : pedidos) {
            if (!dentroDeTopes(cadete, p)) {
                throw new BadRequestException(
                        "El cadete supera su tope de viajes simultáneos o de dinero transportado con este lote.");
            }
            p.setAsignadoPorUsername(adminUsername);
            ofertar(p, cadete);
        }
        return pedidos;
    }

    private void ofertar(Pedido pedido, Cadete cadete) {
        int tiempoLimite = configuracionService.getInt("tiempo_limite_aceptacion_seg", 120);
        Instant ahora = Instant.now();

        OfertaPedido oferta = new OfertaPedido();
        oferta.setId(UUID.randomUUID().toString());
        oferta.setPedido(pedido);
        oferta.setCadete(cadete);
        oferta.setOfrecidoEn(ahora);
        oferta.setExpiraEn(ahora.plusSeconds(tiempoLimite));
        oferta.setResultado(resultado("PENDIENTE"));
        ofertaRepo.save(oferta);

        pedido.setCadeteAsignado(cadete);
        pedido.setEstado(estado("PENDIENTE"));
        pedido.setAsignadoEn(ahora);
        repo.save(pedido);

        // OJO: antes acá se forzaba cadete.estado = OCUPADO, lo que le impedía recibir más
        // viajes aunque tuviera lugar (maxViajesSimultaneos) — el estado LIBRE/OCUPADO ahora
        // es una eleccion manual del cadete (ver CadeteService.actualizarEstado), no algo que
        // el sistema le pise en cada asignación. dentroDeTopes() es lo que limita cuántos
        // viajes puede llevar a la vez.
        PedidoResponse dto = PedidoResponse.from(pedido);
        publisher.publicarEventoViaje(cadete.getId(), "VIAJE_ASIGNADO", dto);
        publisher.publicarPedido(dto);
        fcmService.enviar(cadete.getFcmToken(), "Nuevo viaje",
                "Tenes un viaje nuevo asignado", Map.of("tipo", "VIAJE_ASIGNADO", "pedidoId", pedido.getId()));
    }

    /**
     * Reasigna un pedido PENDIENTE/EN_CURSO a otro cadete en un solo paso (ronda 4, punto
     * 40) — antes había que "Quitar" (vuelve a SIN_ASIGNAR) y asignar de nuevo desde cero.
     */
    public Pedido reasignar(String pedidoId, String nuevoCadeteId, String adminUsername) {
        Pedido pedido = get(pedidoId);
        Cadete actual = pedido.getCadeteAsignado();
        if (actual == null) {
            throw new BadRequestException("El pedido no tiene cadete asignado.");
        }
        Cadete nuevo = cadeteRepo.findById(nuevoCadeteId)
                .orElseThrow(() -> ResourceNotFoundException.of("Cadete", nuevoCadeteId));
        if (nuevo.getId().equals(actual.getId())) {
            throw new BadRequestException("Ese cadete ya tiene el pedido asignado.");
        }
        if (!nuevo.isActivo() || !"LIBRE".equals(nuevo.getEstado().getId())) {
            throw new BadRequestException("El cadete no esta libre.");
        }
        if (!puedeRecibirViajes(nuevo)) {
            throw new BadRequestException("El cadete todavia no pago la cuota semanal — no se le puede asignar.");
        }
        if (!dentroDeTopes(nuevo, pedido)) {
            throw new BadRequestException("El cadete supera su tope de viajes, de dinero transportado o no le alcanza el crédito.");
        }

        cerrarOfertaPendienteSiExiste(pedido, actual, "QUITADO_ADMIN");
        reembolsarComisionSiCorresponde(pedido, actual);
        liberarCadete(actual);
        pedido.setCadeteAsignado(null);
        pedido.setEstado(estado("SIN_ASIGNAR"));
        repo.save(pedido);

        PedidoResponse dtoQuitado = PedidoResponse.from(pedido);
        publisher.publicarEventoViaje(actual.getId(), "VIAJE_QUITADO", dtoQuitado);
        fcmService.enviar(actual.getFcmToken(), "Viaje reasignado", "Se te reasigno el viaje a otro cadete",
                Map.of("tipo", "VIAJE_QUITADO", "pedidoId", pedido.getId()));

        pedido.setAsignadoPorUsername(adminUsername);
        ofertar(pedido, nuevo);
        return pedido;
    }

    /** Boton "Quitar": desasigna manualmente sin anular el pedido, vuelve a SIN_ASIGNAR. */
    public Pedido quitarCadete(String pedidoId) {
        Pedido pedido = get(pedidoId);
        Cadete cadete = pedido.getCadeteAsignado();
        if (cadete == null) {
            throw new BadRequestException("El pedido no tiene cadete asignado.");
        }
        cerrarOfertaPendienteSiExiste(pedido, cadete, "QUITADO_ADMIN");
        reembolsarComisionSiCorresponde(pedido, cadete);
        liberarCadete(cadete);
        pedido.setCadeteAsignado(null);
        pedido.setEstado(estado("SIN_ASIGNAR"));
        repo.save(pedido);

        PedidoResponse dto = PedidoResponse.from(pedido);
        publisher.publicarEventoViaje(cadete.getId(), "VIAJE_QUITADO", dto);
        publisher.publicarPedido(dto);
        fcmService.enviar(cadete.getFcmToken(), "Viaje quitado",
                "Se te quito el viaje", Map.of("tipo", "VIAJE_QUITADO", "pedidoId", pedido.getId()));
        return pedido;
    }

    /** Boton "Anular": cancela el pedido en cualquier estado activo. Motivo ("CLIENTE"/"OTRO") es para métricas. */
    public Pedido cancelar(String pedidoId, String motivo, String adminUsername) {
        Pedido pedido = get(pedidoId);
        if (ESTADOS_FINALES.contains(pedido.getEstado().getId())) {
            throw new BadRequestException("El pedido ya esta cerrado.");
        }
        Cadete cadete = pedido.getCadeteAsignado();
        if (cadete != null) {
            cerrarOfertaPendienteSiExiste(pedido, cadete, "QUITADO_ADMIN");
            reembolsarComisionSiCorresponde(pedido, cadete);
            liberarCadete(cadete);
        }
        pedido.setEstado(estado("CANCELADO"));
        pedido.setMotivoCancelacion(motivo);
        pedido.setCanceladoEn(Instant.now());
        pedido.setCanceladoPorUsername(adminUsername);
        repo.save(pedido);

        PedidoResponse dto = PedidoResponse.from(pedido);
        if (cadete != null) {
            publisher.publicarEventoViaje(cadete.getId(), "VIAJE_QUITADO", dto);
            fcmService.enviar(cadete.getFcmToken(), "Viaje cancelado",
                    "El pedido fue anulado", Map.of("tipo", "VIAJE_QUITADO", "pedidoId", pedido.getId()));
        }
        publisher.publicarPedido(dto);
        return pedido;
    }

    // --- Acciones del cadete ---

    public Pedido aceptar(String pedidoId, String cadeteUsername) {
        Pedido pedido = get(pedidoId);
        Cadete cadete = cadeteDelUsername(cadeteUsername);
        validarPertenencia(pedido, cadete);
        OfertaPedido oferta = ofertaPendiente(pedidoId, cadete.getId());
        if (oferta.getExpiraEn().isBefore(Instant.now())) {
            throw new BadRequestException("El tiempo para aceptar este viaje ya vencio.");
        }
        oferta.setResultado(resultado("ACEPTADO"));
        ofertaRepo.save(oferta);

        if ("PORCENTAJE".equals(cadete.getModalidadPago())) {
            BigDecimal comision = comisionDe(pedido);
            cadete.setCreditoDisponible(cadete.getCreditoDisponible().subtract(comision));
            pedido.setComisionDescontada(comision);
            cadeteRepo.save(cadete);
            registrarMovimientoCredito(cadete, "COMISION", comision, pedido.getId(), pedido.getNumero());
            avisarCreditoBajoSiCorresponde(cadete);
        }

        pedido.setEstado(estado("EN_CURSO"));
        pedido.setAceptadoEn(Instant.now());
        repo.save(pedido);

        publisher.publicarPedido(PedidoResponse.from(pedido));
        smsGatewayService.enviar(pedido.getId(), pedido.getClienteTelefono(), armarSms("sms_template_aceptado",
                "Tu pedido esta en camino, seguilo aca: {link}", pedido));
        webPushService.enviarA(pedido, "Tu pedido está en camino", "Un cadete ya está yendo a buscarlo.");
        return pedido;
    }

    public Pedido rechazar(String pedidoId, String cadeteUsername, String motivo) {
        Pedido pedido = get(pedidoId);
        Cadete cadete = cadeteDelUsername(cadeteUsername);
        validarPertenencia(pedido, cadete);
        OfertaPedido oferta = ofertaPendiente(pedidoId, cadete.getId());
        oferta.setResultado(resultado("RECHAZADO"));
        oferta.setMotivoRechazo(motivo == null || motivo.isBlank() ? null : motivo.trim());
        ofertaRepo.save(oferta);

        publisher.publicarAlertaRechazo(cadete, PedidoResponse.from(pedido));
        liberarYReasignar(pedido, cadete);
        return pedido;
    }

    /** Boton "Retirado": marca que el cadete paso por lo del cliente a buscar el pedido. La foto es opcional. */
    public Pedido registrarRecepcion(String pedidoId, String cadeteUsername, String fotoUrl, Double lat, Double lng) {
        Pedido pedido = get(pedidoId);
        Cadete cadete = cadeteDelUsername(cadeteUsername);
        validarPertenencia(pedido, cadete);
        if (!"EN_CURSO".equals(pedido.getEstado().getId())) {
            throw new BadRequestException("El pedido no esta en curso.");
        }
        if (pedido.getRetiradoEn() != null) {
            throw new BadRequestException("El pedido ya fue marcado como retirado.");
        }
        pedido.setRetiradoEn(Instant.now());
        if (fotoUrl != null && !fotoUrl.isBlank()) {
            pedido.setFotoRecepcionUrl(fotoUrl.trim());
        }
        pedido.setRetiroLat(lat);
        pedido.setRetiroLng(lng);
        repo.save(pedido);
        publisher.publicarPedido(PedidoResponse.from(pedido));
        return pedido;
    }

    /**
     * El cadete marca una parada intermedia como entregada (ronda 3, punto 38: repartos
     * con varias entregas en la misma vuelta) — "Finalizar" sigue siendo el cierre del
     * pedido entero, y exige que todas las paradas ya estén entregadas.
     */
    public Pedido marcarParadaEntregada(String pedidoId, String cadeteUsername, String paradaId) {
        Pedido pedido = get(pedidoId);
        Cadete cadete = cadeteDelUsername(cadeteUsername);
        validarPertenencia(pedido, cadete);
        if (!"EN_CURSO".equals(pedido.getEstado().getId())) {
            throw new BadRequestException("El pedido no esta en curso.");
        }
        PedidoParada parada = pedido.getParadas().stream()
                .filter(p -> p.getId().equals(paradaId))
                .findFirst()
                .orElseThrow(() -> ResourceNotFoundException.of("Parada", paradaId));
        if (parada.getEntregadoEn() == null) {
            parada.setEntregadoEn(Instant.now());
            pedidoParadaRepo.save(parada);
        }
        publisher.publicarPedido(PedidoResponse.from(pedido));
        return pedido;
    }

    public Pedido finalizar(String pedidoId, String cadeteUsername, FinalizarRequest req) {
        Pedido pedido = get(pedidoId);
        Cadete cadete = cadeteDelUsername(cadeteUsername);
        validarPertenencia(pedido, cadete);
        return finalizarInterno(pedido, cadete, req, true);
    }

    /**
     * Cierre manual desde el panel (boton "Finalizar" del dashboard): para cuando el cadete
     * no puede finalizar el viaje el mismo por quedarse sin datos/internet. A diferencia del
     * cierre normal, no exige nombre de receptor ni foto — es una excepcion que confirma el admin.
     */
    public Pedido finalizarComoAdmin(String pedidoId, FinalizarRequest req) {
        Pedido pedido = get(pedidoId);
        Cadete cadete = pedido.getCadeteAsignado();
        if (cadete == null) {
            throw new BadRequestException("El pedido no tiene cadete asignado.");
        }
        return finalizarInterno(pedido, cadete, req, false);
    }

    private Pedido finalizarInterno(Pedido pedido, Cadete cadete, FinalizarRequest req, boolean exigirComprobante) {
        if (!"EN_CURSO".equals(pedido.getEstado().getId())) {
            throw new BadRequestException("El pedido no esta en curso.");
        }
        long paradasSinEntregar = pedido.getParadas().stream().filter(p -> p.getEntregadoEn() == null).count();
        if (exigirComprobante && paradasSinEntregar > 0) {
            throw new BadRequestException("Todavia faltan " + paradasSinEntregar + " parada(s) por entregar.");
        }
        boolean sinReceptor = req.receptorNombre() == null || req.receptorNombre().isBlank();
        boolean sinFoto = req.fotoUrl() == null || req.fotoUrl().isBlank();
        boolean sinFirma = req.firmaUrl() == null || req.firmaUrl().isBlank();
        if (exigirComprobante && (sinReceptor || sinFoto)) {
            throw new BadRequestException("Hace falta el nombre y apellido de quien recibio y una foto de la entrega.");
        }
        if (exigirComprobante && sinFirma && configuracionService.getBoolean("firma_receptor_obligatoria", false)) {
            throw new BadRequestException("Hace falta la firma digital de quien recibio.");
        }
        pedido.setEntregaReceptorNombre(sinReceptor ? null : req.receptorNombre().trim());
        pedido.setEntregaFotoUrl(sinFoto ? null : req.fotoUrl().trim());
        pedido.setFirmaReceptorUrl(sinFirma ? null : req.firmaUrl().trim());
        pedido.setEntregaLat(req.lat());
        pedido.setEntregaLng(req.lng());
        pedido.setEstado(estado("FINALIZADO"));
        pedido.setFinalizadoEn(Instant.now());
        repo.save(pedido);

        liberarCadete(cadete);

        publisher.publicarPedido(PedidoResponse.from(pedido));
        smsGatewayService.enviar(pedido.getId(), pedido.getClienteTelefono(), armarSms("sms_template_finalizado",
                "Tu pedido fue entregado. Mira el detalle, descarga el comprobante y calificanos aca: {link}", pedido));
        webPushService.enviarA(pedido, "Pedido entregado", "Tu pedido fue entregado. Descargá el comprobante y calificanos.");
        return pedido;
    }

    /**
     * Botón "No se pudo entregar" (ej. el cliente no atendió) — distinto de "Finalizar"
     * (no hubo entrega) y de "Anular" (el pedido sigue vivo, el admin lo puede reintentar
     * con {@link #reintentarEntrega} en vez de cargarlo de cero).
     */
    public Pedido marcarNoEntregado(String pedidoId, String cadeteUsername, String motivo) {
        Pedido pedido = get(pedidoId);
        Cadete cadete = cadeteDelUsername(cadeteUsername);
        validarPertenencia(pedido, cadete);
        if (!"EN_CURSO".equals(pedido.getEstado().getId())) {
            throw new BadRequestException("El pedido no esta en curso.");
        }
        pedido.setEstado(estado("NO_ENTREGADO"));
        pedido.setMotivoNoEntrega(motivo == null || motivo.isBlank() ? null : motivo.trim());
        pedido.setNoEntregadoEn(Instant.now());
        reembolsarComisionSiCorresponde(pedido, cadete);
        repo.save(pedido);

        liberarCadete(cadete);

        publisher.publicarPedido(PedidoResponse.from(pedido));
        return pedido;
    }

    /**
     * El admin reintenta la entrega de un pedido NO_ENTREGADO: vuelve a SIN_ASIGNAR para
     * que pase de nuevo por la asignación normal, sin anularlo ni cargarlo de cero.
     */
    public Pedido reintentarEntrega(String pedidoId) {
        Pedido pedido = get(pedidoId);
        if (!"NO_ENTREGADO".equals(pedido.getEstado().getId())) {
            throw new BadRequestException("Este pedido no esta en 'No se pudo entregar'.");
        }
        pedido.setEstado(estado("SIN_ASIGNAR"));
        pedido.setCadeteAsignado(null);
        pedido.setAsignadoEn(null);
        pedido.setAceptadoEn(null);
        pedido.setRetiradoEn(null);
        pedido.setRetiroLat(null);
        pedido.setRetiroLng(null);
        pedido.setFotoRecepcionUrl(null);
        pedido.setAvisoRetiroEnviado(false);
        pedido.setAvisoFinalizacionEnviado(false);
        pedido.setAlertaInactividadEnviada(false);
        repo.save(pedido);

        publisher.publicarPedido(PedidoResponse.from(pedido));
        return pedido;
    }

    // --- Calificacion (pagina publica de seguimiento) ---

    /** El cliente califica desde la pagina publica de seguimiento, una sola vez, cuando el pedido ya esta FINALIZADO. */
    public Pedido calificar(String token, int estrellas, String comentario) {
        Pedido pedido = getPorToken(token);
        if (!"FINALIZADO".equals(pedido.getEstado().getId())) {
            throw new BadRequestException("Todavia no se puede calificar este pedido.");
        }
        if (pedido.getCalificadoEn() != null) {
            throw new BadRequestException("Este pedido ya fue calificado.");
        }
        if (estrellas < 1 || estrellas > 5) {
            throw new BadRequestException("La calificacion tiene que ser de 1 a 5 estrellas.");
        }
        pedido.setCalificacionEstrellas(estrellas);
        pedido.setCalificacionComentario(comentario == null || comentario.isBlank() ? null : comentario.trim());
        pedido.setCalificadoEn(Instant.now());
        return repo.save(pedido);
    }

    // --- Reasignacion automatica (fix del bug, spec sección 4) ---

    private void liberarYReasignar(Pedido pedido, Cadete cadeteQueNoAcepto) {
        liberarCadete(cadeteQueNoAcepto);
        PedidoResponse dtoQuitado = PedidoResponse.from(pedido);
        publisher.publicarEventoViaje(cadeteQueNoAcepto.getId(), "VIAJE_QUITADO", dtoQuitado);
        fcmService.enviar(cadeteQueNoAcepto.getFcmToken(), "Viaje quitado",
                "Se te quito el viaje", Map.of("tipo", "VIAJE_QUITADO", "pedidoId", pedido.getId()));

        Set<String> yaOfertados = ofertaRepo.findByPedidoId(pedido.getId()).stream()
                .map(o -> o.getCadete().getId())
                .collect(Collectors.toSet());
        Optional<Cadete> siguiente = buscarCandidato(pedido, yaOfertados);
        if (siguiente.isPresent()) {
            ofertar(pedido, siguiente.get());
        } else {
            pedido.setCadeteAsignado(null);
            pedido.setEstado(estado("SIN_ASIGNAR"));
            repo.save(pedido);
            publisher.publicarPedido(PedidoResponse.from(pedido));
        }
    }

    /**
     * Se llama cuando el cadete deja un pedido (finaliza, se lo quitan, o se lo reasignan).
     * Ya NO fuerza estado=LIBRE — si el cadete se puso OCUPADO a mano (o sigue con otro
     * viaje en curso), esto no se lo debe pisar. Solo actualiza el orden de la cola FIFO
     * para que rote hacia atrás al terminar algo.
     */
    private void liberarCadete(Cadete cadete) {
        cadete.setOrdenColaEspera(Instant.now());
        cadeteRepo.save(cadete);
    }

    private void cerrarOfertaPendienteSiExiste(Pedido pedido, Cadete cadete, String resultadoId) {
        ofertaRepo.findFirstByPedidoIdAndCadeteIdAndResultadoId(pedido.getId(), cadete.getId(), "PENDIENTE")
                .ifPresent(o -> {
                    o.setResultado(resultado(resultadoId));
                    ofertaRepo.save(o);
                });
    }

    private void validarPertenencia(Pedido pedido, Cadete cadete) {
        if (pedido.getCadeteAsignado() == null || !pedido.getCadeteAsignado().getId().equals(cadete.getId())) {
            throw new BadRequestException("Este viaje ya no te pertenece.");
        }
    }

    private OfertaPedido ofertaPendiente(String pedidoId, String cadeteId) {
        return ofertaRepo.findFirstByPedidoIdAndCadeteIdAndResultadoId(pedidoId, cadeteId, "PENDIENTE")
                .orElseThrow(() -> new BadRequestException("La oferta de este viaje ya no esta vigente."));
    }

    private Cadete cadeteDelUsername(String username) {
        return cadeteRepo.findByUsername(username)
                .orElseThrow(() -> ResourceNotFoundException.of("Cadete", username));
    }

    private String linkSeguimiento(Pedido pedido) {
        return frontBaseUrlSeguimiento + "/seguimiento/" + pedido.getTokenSeguimiento();
    }

    /** Botón "Reenviar SMS" del panel (ronda 3, punto 21): reenvía a mano el link de seguimiento por si el SMS original no llegó. */
    public void reenviarSmsSeguimiento(String pedidoId) {
        Pedido pedido = get(pedidoId);
        smsGatewayService.enviar(pedido.getId(), pedido.getClienteTelefono(), armarSms("sms_template_reenvio",
                "Seguí tu pedido acá: {link}", pedido));
    }

    /** Plantillas de SMS editables desde Configuración (ronda 10, punto 106) — antes estaban hardcodeadas. */
    private String armarSms(String claveConfiguracion, String porDefecto, Pedido pedido) {
        String plantilla = configuracionService.getString(claveConfiguracion, porDefecto);
        return plantilla.replace("{link}", linkSeguimiento(pedido));
    }

    // --- Jobs programados ---

    /** Reasigna automaticamente los viajes cuya oferta vencio sin respuesta (spec sección 4). */
    @Scheduled(fixedDelay = 15_000)
    @Transactional
    public void expirarOfertasVencidas() {
        List<OfertaPedido> vencidas = ofertaRepo.findByResultadoIdAndExpiraEnBefore("PENDIENTE", Instant.now());
        for (OfertaPedido oferta : vencidas) {
            oferta.setResultado(resultado("EXPIRADO"));
            ofertaRepo.save(oferta);
            liberarYReasignar(oferta.getPedido(), oferta.getCadete());
        }
    }

    /**
     * Si "asignacion_automatica" está prendida desde el panel (Configuración), ofrece
     * cada pedido SIN_ASIGNAR al mejor candidato solo, sin esperar a que el admin
     * apriete "Asignar". Apagada por defecto — reusa exactamente la misma lógica de
     * sugerencia/oferta que usa el boton manual (buscarCandidato + ofertar).
     */
    @Scheduled(fixedDelay = 20_000)
    @Transactional
    public void asignarAutomaticamente() {
        if (!configuracionService.getBoolean("asignacion_automatica", false)) return;
        List<Pedido> sinAsignar = repo.findByEstadoIdInOrderByCreadoEnDesc(List.of("SIN_ASIGNAR"));
        for (Pedido p : sinAsignar) {
            Set<String> yaOfertados = ofertaRepo.findByPedidoId(p.getId()).stream()
                    .map(o -> o.getCadete().getId())
                    .collect(Collectors.toSet());
            buscarCandidato(p, yaOfertados).ifPresent(c -> ofertar(p, c));
        }
    }

    /**
     * Recordatorios al cadete si un viaje EN_CURSO se está demorando (spec: umbrales
     * configurables desde el panel — Configuración). Cada aviso se manda una sola vez
     * por pedido (flags avisoRetiroEnviado/avisoFinalizacionEnviado), no en cada corrida.
     */
    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void avisosDemora() {
        int minRetiro = configuracionService.getInt("alerta_demora_retiro_min", 30);
        int minFinalizacion = configuracionService.getInt("alerta_demora_finalizacion_min", 60);
        Instant ahora = Instant.now();

        for (Pedido p : repo.findByEstadoIdInOrderByCreadoEnDesc(List.of("EN_CURSO"))) {
            Cadete cadete = p.getCadeteAsignado();
            if (cadete == null || p.getAceptadoEn() == null) continue;
            long minutosDesdeAceptado = Duration.between(p.getAceptadoEn(), ahora).toMinutes();

            if (!p.isAvisoRetiroEnviado() && p.getRetiradoEn() == null && minutosDesdeAceptado >= minRetiro) {
                avisarDemora(cadete, "Tenés el pedido Nº " + p.getNumero()
                        + " hace más de " + minRetiro + " minutos sin marcar que lo retiraste.");
                p.setAvisoRetiroEnviado(true);
            }
            if (!p.isAvisoFinalizacionEnviado() && minutosDesdeAceptado >= minFinalizacion) {
                avisarDemora(cadete, "Tenés el pedido Nº " + p.getNumero()
                        + " hace más de " + minFinalizacion + " minutos sin finalizar. Por favor, finalizalo.");
                p.setAvisoFinalizacionEnviado(true);
            }
            repo.save(p);
        }
    }

    private void avisarDemora(Cadete cadete, String mensaje) {
        publisher.publicarAviso(cadete.getId(), mensaje);
        fcmService.enviar(cadete.getFcmToken(), "Recordatorio", mensaje, Map.of("tipo", "RECORDATORIO_PEDIDO"));
    }

    /**
     * Alerta al admin si un cadete con un viaje EN_CURSO (ya retiró el pedido, está en la
     * calle) no manda una ubicación nueva hace rato — posible batería muerta, zona sin
     * señal, o abandonó el pedido sin avisar. Complementa los avisos de demora (arriba),
     * que son sobre el pedido; este es sobre el cadete. Una sola alerta por pedido.
     */
    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void alertaInactividadSospechosa() {
        int minInactividad = configuracionService.getInt("alerta_inactividad_min", 20);
        Instant ahora = Instant.now();

        for (Pedido p : repo.findByEstadoIdInOrderByCreadoEnDesc(List.of("EN_CURSO"))) {
            if (p.isAlertaInactividadEnviada() || p.getRetiradoEn() == null) continue;
            Cadete cadete = p.getCadeteAsignado();
            if (cadete == null || cadete.getUbicacionActualizadaEn() == null) continue;
            long minutosSinUbicacion = Duration.between(cadete.getUbicacionActualizadaEn(), ahora).toMinutes();
            if (minutosSinUbicacion >= minInactividad) {
                publisher.publicarAlertaInactividad(cadete, PedidoResponse.from(p), minutosSinUbicacion);
                p.setAlertaInactividadEnviada(true);
                repo.save(p);
            }
        }
    }

    /** Pasa los pedidos programados a SIN_ASIGNAR cuando llega su fecha (panel: "Pedidos programados"). */
    @Scheduled(fixedDelay = 30_000)
    @Transactional
    public void activarPedidosProgramados() {
        List<Pedido> listos = repo.findByEstadoIdAndFechaProgramadaLessThanEqual("PROGRAMADO", Instant.now());
        for (Pedido p : listos) {
            p.setEstado(estado("SIN_ASIGNAR"));
            repo.save(p);
            PedidoResponse dto = PedidoResponse.from(p);
            publisher.publicarPedido(dto);
            publisher.publicarAlertaPedidoNuevo(dto);
        }
    }

    // --- Helpers de lookup ---

    private EstadoPedido estado(String id) {
        return estadoPedidoRepo.findById(id)
                .orElseThrow(() -> new IllegalStateException("Falta seedear estado_pedido." + id));
    }

    private ResultadoOferta resultado(String id) {
        return resultadoOfertaRepo.findById(id)
                .orElseThrow(() -> new IllegalStateException("Falta seedear resultado_oferta." + id));
    }

    private com.cadeteria.backend.model.EstadoCadete estadoCadete(String id) {
        return estadoCadeteRepo.findById(id)
                .orElseThrow(() -> new IllegalStateException("Falta seedear estado_cadete." + id));
    }
}
