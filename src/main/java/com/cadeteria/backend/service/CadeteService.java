package com.cadeteria.backend.service;

import com.cadeteria.backend.common.BadRequestException;
import com.cadeteria.backend.common.ResourceNotFoundException;
import com.cadeteria.backend.dto.CadeteDtos.AvisoGeneralResponse;
import com.cadeteria.backend.dto.CadeteDtos.CadeteFichaResponse;
import com.cadeteria.backend.dto.CadeteDtos.CadeteRequest;
import com.cadeteria.backend.dto.CadeteDtos.CadeteResponse;
import com.cadeteria.backend.model.AvisoGeneral;
import com.cadeteria.backend.model.AvisoGeneralLectura;
import com.cadeteria.backend.model.Cadete;
import com.cadeteria.backend.model.CadeteEstadoLog;
import com.cadeteria.backend.model.EstadoCadete;
import com.cadeteria.backend.model.MovimientoCredito;
import com.cadeteria.backend.model.Pedido;
import com.cadeteria.backend.model.PedidoUbicacion;
import com.cadeteria.backend.model.TipoVehiculo;
import com.cadeteria.backend.repository.AvisoGeneralLecturaRepository;
import com.cadeteria.backend.repository.AvisoGeneralRepository;
import com.cadeteria.backend.repository.CadeteEstadoLogRepository;
import com.cadeteria.backend.repository.CadeteRepository;
import com.cadeteria.backend.repository.EstadoCadeteRepository;
import com.cadeteria.backend.repository.MovimientoCreditoRepository;
import com.cadeteria.backend.repository.PedidoRepository;
import com.cadeteria.backend.repository.PedidoUbicacionRepository;
import com.cadeteria.backend.repository.TipoVehiculoRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class CadeteService {

    private final CadeteRepository repo;
    private final TipoVehiculoRepository tipoVehiculoRepo;
    private final EstadoCadeteRepository estadoCadeteRepo;
    private final PasswordEncoder passwordEncoder;
    private final GeocodingService geocodingService;
    private final CadeteSesionService sesionService;
    private final WebSocketPublisher publisher;
    private final FcmService fcmService;
    private final PedidoRepository pedidoRepo;
    private final PedidoUbicacionRepository pedidoUbicacionRepo;
    private final AvisoGeneralRepository avisoRepo;
    private final AvisoGeneralLecturaRepository avisoLecturaRepo;
    private final ConfiguracionService configuracionService;
    private final CadeteEstadoLogRepository estadoLogRepo;
    private final MovimientoCreditoRepository movimientoCreditoRepo;
    private final MetricasService metricasService;
    private final IncidenciaService incidenciaService;

    public CadeteService(CadeteRepository repo, TipoVehiculoRepository tipoVehiculoRepo,
                          EstadoCadeteRepository estadoCadeteRepo, PasswordEncoder passwordEncoder,
                          GeocodingService geocodingService, CadeteSesionService sesionService,
                          WebSocketPublisher publisher, FcmService fcmService,
                          PedidoRepository pedidoRepo, PedidoUbicacionRepository pedidoUbicacionRepo,
                          AvisoGeneralRepository avisoRepo, AvisoGeneralLecturaRepository avisoLecturaRepo,
                          ConfiguracionService configuracionService, CadeteEstadoLogRepository estadoLogRepo,
                          MovimientoCreditoRepository movimientoCreditoRepo, MetricasService metricasService,
                          IncidenciaService incidenciaService) {
        this.repo = repo;
        this.tipoVehiculoRepo = tipoVehiculoRepo;
        this.estadoCadeteRepo = estadoCadeteRepo;
        this.passwordEncoder = passwordEncoder;
        this.geocodingService = geocodingService;
        this.sesionService = sesionService;
        this.avisoRepo = avisoRepo;
        this.avisoLecturaRepo = avisoLecturaRepo;
        this.publisher = publisher;
        this.fcmService = fcmService;
        this.pedidoRepo = pedidoRepo;
        this.pedidoUbicacionRepo = pedidoUbicacionRepo;
        this.configuracionService = configuracionService;
        this.estadoLogRepo = estadoLogRepo;
        this.movimientoCreditoRepo = movimientoCreditoRepo;
        this.metricasService = metricasService;
        this.incidenciaService = incidenciaService;
    }

    /**
     * Panorama completo del cadete para su ficha en el panel: viajes
     * finalizados/rechazados/no-aceptados en [desde, hasta) (estos dos últimos son los
     * que se le reasignaron a otro cadete — filtrable por día/semana/mes/todo desde el
     * front), sus incidencias (con link al pedido si corresponde) y el historial
     * completo de altas/bajas — estos dos últimos son siempre de todo el historial, no
     * del rango elegido.
     */
    @Transactional(readOnly = true)
    public CadeteFichaResponse ficha(String id, Instant desde, Instant hasta) {
        Cadete c = get(id);
        var estadisticas = metricasService.metricasCadetes(desde, hasta).stream()
                .filter(m -> m.cadeteId().equals(id))
                .findFirst()
                .orElseThrow();
        var incidencias = incidenciaService.porCadete(id).stream()
                .map(com.cadeteria.backend.dto.IncidenciaDtos.IncidenciaResponse::from)
                .toList();
        var historial = estadoLogRepo.findByCadeteIdOrderByCambiadoEnDesc(id).stream()
                .map(l -> new com.cadeteria.backend.dto.CadeteDtos.CadeteEstadoLogResponse(
                        l.getId(), l.isActivo(), l.getMotivo(), l.getCambiadoEn(), l.getCambiadoPorUsername()))
                .toList();
        return new CadeteFichaResponse(toResponse(c), estadisticas, incidencias, historial);
    }

    /** Cadete + calificación histórica (todo el registro) — para la lista/detalle del panel admin. */
    @Transactional(readOnly = true)
    public CadeteResponse toResponse(Cadete c) {
        List<Pedido> calificados = pedidoRepo.findByCadeteAsignadoIdAndCalificacionEstrellasIsNotNull(c.getId());
        Double promedio = calificados.isEmpty() ? null
                : calificados.stream().mapToInt(Pedido::getCalificacionEstrellas).average().orElse(0);
        return CadeteResponse.from(c, promedio, calificados.size());
    }

    @Transactional(readOnly = true)
    public List<Cadete> findAll() {
        return repo.findAll();
    }

    @Transactional(readOnly = true)
    public Cadete get(String id) {
        return repo.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Cadete", id));
    }

    @Transactional(readOnly = true)
    public Cadete getByUsername(String username) {
        return repo.findByUsername(username).orElseThrow(() -> ResourceNotFoundException.of("Cadete", username));
    }

    public Cadete create(CadeteRequest req) {
        if (req.password() == null || req.password().isBlank()) {
            throw new BadRequestException("La contrasena es obligatoria al crear un cadete.");
        }
        Cadete c = new Cadete();
        c.setId(UUID.randomUUID().toString());
        c.setPasswordHash(passwordEncoder.encode(req.password()));
        c.setEstado(estadoLibre());
        apply(c, req);
        return repo.save(c);
    }

    public Cadete update(String id, CadeteRequest req) {
        Cadete c = get(id);
        if (req.password() != null && !req.password().isBlank()) {
            c.setPasswordHash(passwordEncoder.encode(req.password()));
        }
        apply(c, req);
        return repo.save(c);
    }

    /** motivo + registro en el historial (ronda 10, punto 96) — antes no quedaba ningún rastro de altas/bajas. */
    public Cadete setActivo(String id, boolean activo, String motivo, String cambiadoPorUsername) {
        Cadete c = get(id);
        c.setActivo(activo);
        repo.save(c);

        CadeteEstadoLog log = new CadeteEstadoLog();
        log.setId(UUID.randomUUID().toString());
        log.setCadeteId(id);
        log.setActivo(activo);
        log.setMotivo(motivo == null || motivo.isBlank() ? null : motivo.trim());
        log.setCambiadoEn(Instant.now());
        log.setCambiadoPorUsername(cambiadoPorUsername);
        estadoLogRepo.save(log);
        return c;
    }

    @Transactional(readOnly = true)
    public List<CadeteEstadoLog> historialEstado(String id) {
        return estadoLogRepo.findByCadeteIdOrderByCambiadoEnDesc(id);
    }

    @Transactional(readOnly = true)
    public List<MovimientoCredito> historialCredito(String id) {
        return movimientoCreditoRepo.findByCadeteIdOrderByCreadoEnDesc(id);
    }

    /**
     * Marca al cadete como LIBRE a mano desde el panel — para cuando todavia no existe
     * la app de cadetes (que en el futuro seria quien reporte esto solo) o el cadete
     * quedo trabado en otro estado por algun error.
     */
    public Cadete marcarLibre(String id) {
        Cadete c = get(id);
        boolean estabaDesconectado = "DESCONECTADO".equals(c.getEstado().getId());
        c.setEstado(estadoLibre());
        c.setOrdenColaEspera(Instant.now());
        Cadete guardado = repo.save(c);
        if (estabaDesconectado) sesionService.abrir(guardado);
        return guardado;
    }

    /** Abre/cierra la sesión "online" (CadeteSesionService) cuando el cambio cruza el límite de DESCONECTADO. */
    public Cadete actualizarEstado(String username, String estadoId) {
        Cadete c = getByUsername(username);
        String anterior = c.getEstado().getId();
        EstadoCadete estado = estadoCadeteRepo.findById(estadoId)
                .orElseThrow(() -> ResourceNotFoundException.of("Estado de cadete", estadoId));
        if ("LIBRE".equals(estadoId) && "DESCONECTADO".equals(anterior)) {
            validarDocumentacionCompleta(c);
        }
        c.setEstado(estado);
        if ("LIBRE".equals(estadoId)) {
            c.setOrdenColaEspera(Instant.now());
        }
        Cadete guardado = repo.save(c);

        boolean pasaADesconectado = "DESCONECTADO".equals(estadoId) && !"DESCONECTADO".equals(anterior);
        boolean saleDeDesconectado = !"DESCONECTADO".equals(estadoId) && "DESCONECTADO".equals(anterior);
        if (pasaADesconectado) sesionService.cerrar(guardado);
        if (saleDeDesconectado) sesionService.abrir(guardado);
        return guardado;
    }

    /**
     * Checklist de documentación obligatoria antes de activarse (mejora pedida por el
     * dueño 2026-09-16) — antes se podía activar sin nada cargado (carnet, tarjeta verde,
     * foto del vehículo). Solo se exige al pasar de DESCONECTADO a LIBRE (el gesto real de
     * "activarme" al arrancar un turno), no en cada transición de estado. Apagado por
     * default (`checklist_documentacion_obligatorio`), igual que `firma_receptor_obligatoria`.
     */
    private void validarDocumentacionCompleta(Cadete c) {
        if (!configuracionService.getBoolean("checklist_documentacion_obligatorio", false)) return;
        List<String> faltantes = new java.util.ArrayList<>();
        if (blank(c.getFotoCarnetUrl())) faltantes.add("carnet de conducir");
        if (blank(c.getFotoTarjetaVerdeUrl())) faltantes.add("tarjeta verde");
        if (blank(c.getFotoVehiculoUrl())) faltantes.add("foto del vehículo");
        if (!faltantes.isEmpty()) {
            throw new BadRequestException(
                    "Te falta cargar: " + String.join(", ", faltantes) + ". Pedile al admin que te ayude a completarlo antes de activarte.");
        }
    }

    private boolean blank(String s) {
        return s == null || s.isBlank();
    }

    public Cadete actualizarUbicacion(String username, double lat, double lng) {
        Cadete c = getByUsername(username);
        c.setLat(lat);
        c.setLng(lng);
        c.setUbicacionActualizadaEn(Instant.now());
        c.setZonaActual(geocodingService.resolverZona(lat, lng).orElse(c.getZonaActual()));
        Cadete guardado = repo.save(c);
        registrarPuntoDeTrayecto(guardado, lat, lng);
        return guardado;
    }

    /**
     * Guarda un punto del trayecto real para cada pedido EN_CURSO del cadete (spec Métricas:
     * km real y reconstrucción del recorrido) — no requiere ningún cambio en la app, que ya
     * manda su ubicación periódicamente mientras está activa.
     */
    private void registrarPuntoDeTrayecto(Cadete cadete, double lat, double lng) {
        List<Pedido> enCurso = pedidoRepo.findByCadeteAsignadoIdAndEstadoIdIn(cadete.getId(), List.of("EN_CURSO"));
        for (Pedido pedido : enCurso) {
            PedidoUbicacion punto = new PedidoUbicacion();
            punto.setId(UUID.randomUUID().toString());
            punto.setPedido(pedido);
            punto.setLat(lat);
            punto.setLng(lng);
            pedidoUbicacionRepo.save(punto);
        }
    }

    public Cadete actualizarFcmToken(String username, String fcmToken) {
        Cadete c = getByUsername(username);
        c.setFcmToken(fcmToken);
        return repo.save(c);
    }

    /** El cadete cambia su propia contraseña desde la app — pide la actual para confirmar que es él. */
    public Cadete cambiarPassword(String username, String actual, String nueva) {
        Cadete c = getByUsername(username);
        if (!passwordEncoder.matches(actual, c.getPasswordHash())) {
            throw new BadRequestException("La contraseña actual no es correcta.");
        }
        if (nueva == null || nueva.isBlank()) {
            throw new BadRequestException("La contraseña nueva no puede estar vacía.");
        }
        c.setPasswordHash(passwordEncoder.encode(nueva));
        return repo.save(c);
    }

    /** El cadete cambia su propio teléfono desde la app (si cambia de celular/línea). */
    public Cadete actualizarTelefonoPropio(String username, String telefono) {
        Cadete c = getByUsername(username);
        c.setTelefono(telefono.trim());
        return repo.save(c);
    }

    /** El cadete carga sus propios datos de cobro, para que el cliente le transfiera. */
    public Cadete actualizarCuenta(String username, String cbu, String aliasCbu) {
        Cadete c = getByUsername(username);
        c.setCbu(blankToNull(cbu));
        c.setAliasCbu(blankToNull(aliasCbu));
        return repo.save(c);
    }

    /**
     * Aviso general del admin a todos los cadetes conectados ahora mismo (LIBRE u
     * OCUPADO, no DESCONECTADO) — para avisos operativos tipo "cerramos temprano".
     * Va por WebSocket (si tienen la app abierta) y push (si no); queda registrado para
     * que el panel pueda ver cuántos lo confirmaron.
     */
    public AvisoGeneral enviarAvisoGeneral(String mensaje) {
        List<Cadete> destinatarios = repo.findAll().stream()
                .filter(c -> c.isActivo() && !"DESCONECTADO".equals(c.getEstado().getId()))
                .toList();

        AvisoGeneral aviso = new AvisoGeneral();
        aviso.setId(UUID.randomUUID().toString());
        aviso.setMensaje(mensaje);
        aviso.setTotalDestinatarios(destinatarios.size());
        avisoRepo.save(aviso);

        for (Cadete c : destinatarios) {
            publisher.publicarAviso(c.getId(), mensaje, aviso.getId());
            fcmService.enviar(c.getFcmToken(), "Aviso", mensaje,
                    java.util.Map.of("tipo", "AVISO_GENERAL", "avisoId", aviso.getId()));
        }
        return aviso;
    }

    /** El cadete confirma que vio un aviso general (spec: registrar quién lo vio) — idempotente. */
    public void marcarAvisoLeido(String cadeteUsername, String avisoId) {
        Cadete cadete = getByUsername(cadeteUsername);
        if (avisoLecturaRepo.findByAvisoIdAndCadeteId(avisoId, cadete.getId()).isPresent()) return;
        AvisoGeneral aviso = avisoRepo.findById(avisoId).orElseThrow(() -> ResourceNotFoundException.of("AvisoGeneral", avisoId));
        AvisoGeneralLectura lectura = new AvisoGeneralLectura();
        lectura.setId(UUID.randomUUID().toString());
        lectura.setAviso(aviso);
        lectura.setCadete(cadete);
        avisoLecturaRepo.save(lectura);
    }

    /** Últimos avisos generales con cuántos cadetes de los destinatarios ya lo confirmaron — para el panel. */
    @Transactional(readOnly = true)
    public List<AvisoGeneralResponse> listarAvisos() {
        return avisoRepo.findTop20ByOrderByEnviadoEnDesc().stream()
                .map(a -> AvisoGeneralResponse.from(a, avisoLecturaRepo.countByAvisoId(a.getId())))
                .toList();
    }

    /**
     * Avisos generales que este cadete todavía no vio (ronda 10, punto 98) — antes un
     * aviso solo llegaba a los cadetes conectados en el momento de mandarlo; uno
     * DESCONECTADO nunca se enteraba, ni siquiera al reconectarse después. Se llama al
     * abrir la app / reconectar.
     */
    @Transactional(readOnly = true)
    public List<AvisoGeneralResponse> avisosPendientesDe(String cadeteUsername) {
        Cadete cadete = getByUsername(cadeteUsername);
        java.util.Set<String> yaVistos = new java.util.HashSet<>(avisoLecturaRepo.avisoIdsVistosPor(cadete.getId()));
        return avisoRepo.findTop20ByOrderByEnviadoEnDesc().stream()
                .filter(a -> !yaVistos.contains(a.getId()))
                .map(a -> AvisoGeneralResponse.from(a, avisoLecturaRepo.countByAvisoId(a.getId())))
                .toList();
    }

    /**
     * Pantalla "Avisos" de la app con historial (mejora 2026-09-16) — a diferencia de
     * {@link #avisosPendientesDe}, trae los últimos 20 avisos generales SIN filtrar los
     * ya leídos, marcando cuáles ya vio este cadete, para que pueda volver a leer uno
     * viejo (antes, un aviso que ya se marcó leído desaparecía para siempre).
     */
    @Transactional(readOnly = true)
    public List<com.cadeteria.backend.dto.CadeteDtos.AvisoGeneralHistorialResponse> historialAvisosDe(String cadeteUsername) {
        Cadete cadete = getByUsername(cadeteUsername);
        java.util.Set<String> vistos = new java.util.HashSet<>(avisoLecturaRepo.avisoIdsVistosPor(cadete.getId()));
        return avisoRepo.findTop20ByOrderByEnviadoEnDesc().stream()
                .map(a -> com.cadeteria.backend.dto.CadeteDtos.AvisoGeneralHistorialResponse.from(a, vistos.contains(a.getId())))
                .toList();
    }

    // --- Modelo de cobro del cadete (ronda 7) ---

    /**
     * El admin habilita a un cadete SEMANAL para trabajar esta semana, con pago completo
     * o parcial. Si paga menos del monto configurado, hace falta un `venceEn` — si no
     * completa el resto para esa fecha/hora, {@link #revisarVencimientosPagoSemanal} lo
     * vuelve a deshabilitar solo.
     */
    public Cadete habilitarPagoSemanal(String id, BigDecimal montoPagado, Instant venceEn, BigDecimal montoSemanalNuevo) {
        Cadete c = get(id);
        if (montoSemanalNuevo != null) {
            c.setMontoSemanalActual(montoSemanalNuevo);
        }
        BigDecimal montoSemanal = c.getMontoSemanalActual() != null
                ? c.getMontoSemanalActual()
                : configuracionService.getBigDecimal("pago_semanal_monto", BigDecimal.valueOf(5000));
        boolean pagoCompleto = montoPagado.compareTo(montoSemanal) >= 0;
        if (!pagoCompleto && venceEn == null) {
            throw new BadRequestException("Si el pago es parcial, hace falta poner hasta cuándo tiene para completarlo.");
        }
        c.setPagoSemanalMontoPagado(montoPagado);
        c.setPagoSemanalVenceEn(pagoCompleto ? null : venceEn);
        c.setHabilitadoPago(true);
        return repo.save(c);
    }

    /** Mejora: cambiar el modelo de cobro desde la pantalla "Pagos" unificada, sin pasar por el form completo de edición. */
    public Cadete cambiarModalidadPago(String id, String modalidadPago) {
        if (!"SEMANAL".equals(modalidadPago) && !"PORCENTAJE".equals(modalidadPago)) {
            throw new BadRequestException("Modalidad de pago invalida: " + modalidadPago);
        }
        Cadete c = get(id);
        c.setModalidadPago(modalidadPago);
        return repo.save(c);
    }

    /** El admin carga crédito a un cadete PORCENTAJE tras recibir su transferencia. */
    public Cadete acreditar(String id, BigDecimal monto, String adminUsername) {
        if (monto == null || monto.signum() <= 0) {
            throw new BadRequestException("El monto a acreditar tiene que ser mayor a cero.");
        }
        Cadete c = get(id);
        c.setCreditoDisponible(c.getCreditoDisponible().add(monto));
        c.setAlertaCreditoBajoEnviada(false);
        repo.save(c);
        registrarMovimientoCredito(c, "ACREDITACION", monto, null, null, adminUsername);
        return c;
    }

    /** Registro del historial de crédito (ronda 10, punto 94) — llamado también desde PedidoService al descontar comisión. */
    public void registrarMovimientoCredito(Cadete c, String tipo, BigDecimal monto, String pedidoId, Long pedidoNumero, String creadoPorUsername) {
        MovimientoCredito m = new MovimientoCredito();
        m.setId(UUID.randomUUID().toString());
        m.setCadeteId(c.getId());
        m.setTipo(tipo);
        m.setMonto(monto);
        m.setSaldoResultante(c.getCreditoDisponible());
        m.setPedidoId(pedidoId);
        m.setPedidoNumero(pedidoNumero);
        m.setCreadoEn(Instant.now());
        m.setCreadoPorUsername(creadoPorUsername);
        movimientoCreditoRepo.save(m);
    }

    /**
     * Todos los cadetes SEMANAL arrancan deshabilitados cada lunes — vuelven a pagar la
     * cuota de la semana nueva antes de poder trabajar (ronda 7).
     */
    @Scheduled(cron = "0 0 0 * * MON", zone = "America/Argentina/Buenos_Aires")
    public void reiniciarPagoSemanal() {
        List<Cadete> semanales = repo.findAll().stream()
                .filter(c -> "SEMANAL".equals(c.getModalidadPago()))
                .toList();
        for (Cadete c : semanales) {
            c.setHabilitadoPago(false);
            c.setPagoSemanalMontoPagado(null);
            c.setPagoSemanalVenceEn(null);
            repo.save(c);
            fcmService.enviar(c.getFcmToken(), "Nueva semana",
                    "Empezó la semana — pagá la cuota semanal para poder recibir viajes.",
                    java.util.Map.of("tipo", "PAGO_SEMANAL_PENDIENTE"));
        }
    }

    /**
     * Si un cadete SEMANAL fue habilitado con pago parcial y venció el plazo para
     * completar el resto sin que lo haya pagado, se lo deshabilita solo (ronda 7).
     */
    @Scheduled(fixedDelay = 300_000)
    public void revisarVencimientosPagoSemanal() {
        BigDecimal montoSemanal = configuracionService.getBigDecimal("pago_semanal_monto", BigDecimal.valueOf(5000));
        Instant ahora = Instant.now();
        List<Cadete> vencidos = repo.findAll().stream()
                .filter(c -> "SEMANAL".equals(c.getModalidadPago()))
                .filter(Cadete::isHabilitadoPago)
                .filter(c -> c.getPagoSemanalVenceEn() != null && c.getPagoSemanalVenceEn().isBefore(ahora))
                .filter(c -> c.getPagoSemanalMontoPagado() == null || c.getPagoSemanalMontoPagado().compareTo(montoSemanal) < 0)
                .toList();
        for (Cadete c : vencidos) {
            c.setHabilitadoPago(false);
            c.setPagoSemanalVenceEn(null);
            repo.save(c);
            fcmService.enviar(c.getFcmToken(), "Pago semanal vencido",
                    "No completaste el pago de la cuota semanal a tiempo — te deshabilitamos hasta que la pagues.",
                    java.util.Map.of("tipo", "PAGO_SEMANAL_PENDIENTE"));
        }
    }

    private EstadoCadete estadoLibre() {
        return estadoCadeteRepo.findById("LIBRE")
                .orElseThrow(() -> new IllegalStateException("Falta seedear estado_cadete.LIBRE"));
    }

    private void apply(Cadete c, CadeteRequest req) {
        c.setNombre(req.nombre().trim());
        c.setApellido(req.apellido().trim());
        c.setDni(req.dni().trim());
        c.setTelefono(req.telefono().trim());
        c.setEmail(blankToNull(req.email()));
        c.setFotoUrl(blankToNull(req.fotoUrl()));
        TipoVehiculo tipo = tipoVehiculoRepo.findById(req.tipoVehiculoId())
                .orElseThrow(() -> ResourceNotFoundException.of("Tipo de vehiculo", req.tipoVehiculoId()));
        c.setTipoVehiculo(tipo);
        c.setVehiculoColor(blankToNull(req.vehiculoColor()));
        c.setVehiculoPatente(blankToNull(req.vehiculoPatente()));
        c.setVehiculoMarca(blankToNull(req.vehiculoMarca()));
        c.setVehiculoModelo(blankToNull(req.vehiculoModelo()));
        c.setVehiculoAnio(req.vehiculoAnio());
        c.setFotoVehiculoUrl(blankToNull(req.fotoVehiculoUrl()));
        c.setFotoCarnetUrl(blankToNull(req.fotoCarnetUrl()));
        c.setFotoTarjetaVerdeUrl(blankToNull(req.fotoTarjetaVerdeUrl()));
        c.setUsername(req.username().trim());
        c.setMontoMaximoTransportado(req.montoMaximoTransportado());
        c.setMaxViajesSimultaneos(req.maxViajesSimultaneos());
        c.setMaxViajesDiarios(req.maxViajesDiarios());
        c.setMaxViajesSemanales(req.maxViajesSemanales());
        c.setTurnoInicio(req.turnoInicio());
        c.setTurnoFin(req.turnoFin());
        c.setModalidadPago(req.modalidadPago() == null || req.modalidadPago().isBlank() ? "SEMANAL" : req.modalidadPago());
        c.setNotasInternas(blankToNull(req.notasInternas()));
    }

    private static String blankToNull(String v) {
        return (v == null || v.isBlank()) ? null : v.trim();
    }
}
