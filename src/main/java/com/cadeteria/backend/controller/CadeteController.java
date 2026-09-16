package com.cadeteria.backend.controller;

import com.cadeteria.backend.dto.CadeteDtos.AcreditarRequest;
import com.cadeteria.backend.dto.CadeteDtos.ActivoRequest;
import com.cadeteria.backend.dto.CadeteDtos.AvisoGeneralRequest;
import com.cadeteria.backend.dto.CadeteDtos.AvisoGeneralResponse;
import com.cadeteria.backend.dto.CadeteDtos.CadeteEstadoLogResponse;
import com.cadeteria.backend.dto.CadeteDtos.CadeteRequest;
import com.cadeteria.backend.dto.CadeteDtos.CadeteResponse;
import com.cadeteria.backend.dto.CadeteDtos.HabilitarPagoSemanalRequest;
import com.cadeteria.backend.dto.CadeteDtos.MovimientoCreditoResponse;
import com.cadeteria.backend.dto.CadeteDtos.CambiarPasswordRequest;
import com.cadeteria.backend.dto.CadeteDtos.CuentaRequest;
import com.cadeteria.backend.dto.CadeteDtos.EstadoRequest;
import com.cadeteria.backend.dto.CadeteDtos.FcmTokenRequest;
import com.cadeteria.backend.dto.CadeteDtos.TelefonoRequest;
import com.cadeteria.backend.dto.CadeteDtos.UbicacionRequest;
import com.cadeteria.backend.dto.ConfiguracionDtos.CadeteConfigResponse;
import com.cadeteria.backend.dto.PagoSemanalDtos.MiSemanaResponse;
import com.cadeteria.backend.model.Cadete;
import com.cadeteria.backend.service.CadeteService;
import com.cadeteria.backend.service.ConfiguracionService;
import com.cadeteria.backend.service.PagoSemanalService;
import com.cadeteria.backend.service.WebSocketPublisher;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
public class CadeteController {

    private final CadeteService service;
    private final WebSocketPublisher publisher;
    private final ConfiguracionService configuracionService;
    private final PagoSemanalService pagoSemanalService;

    public CadeteController(CadeteService service, WebSocketPublisher publisher, ConfiguracionService configuracionService,
                             PagoSemanalService pagoSemanalService) {
        this.service = service;
        this.publisher = publisher;
        this.configuracionService = configuracionService;
        this.pagoSemanalService = pagoSemanalService;
    }

    // --- Admin ---

    @GetMapping("/api/admin/cadetes")
    public List<CadeteResponse> list() {
        return service.findAll().stream().map(service::toResponse).toList();
    }

    @GetMapping("/api/admin/cadetes/{id}")
    public CadeteResponse get(@PathVariable String id) {
        return service.toResponse(service.get(id));
    }

    @PostMapping("/api/admin/cadetes")
    public ResponseEntity<CadeteResponse> create(@Valid @RequestBody CadeteRequest req) {
        return ResponseEntity.status(201).body(CadeteResponse.from(service.create(req)));
    }

    @PutMapping("/api/admin/cadetes/{id}")
    public CadeteResponse update(@PathVariable String id, @Valid @RequestBody CadeteRequest req) {
        return CadeteResponse.from(service.update(id, req));
    }

    /** motivo opcional + queda registrado en el historial (ronda 10, punto 96). */
    @PatchMapping("/api/admin/cadetes/{id}/activo")
    public CadeteResponse setActivo(@PathVariable String id, @RequestBody ActivoRequest req, Authentication auth) {
        return CadeteResponse.from(service.setActivo(id, req.activo(), req.motivo(), auth.getName()));
    }

    /** Historial de altas/bajas de un cadete (ronda 10, punto 96). */
    @GetMapping("/api/admin/cadetes/{id}/historial-estado")
    public List<CadeteEstadoLogResponse> historialEstado(@PathVariable String id) {
        return service.historialEstado(id).stream()
                .map(l -> new CadeteEstadoLogResponse(l.getId(), l.isActivo(), l.getMotivo(), l.getCambiadoEn(), l.getCambiadoPorUsername()))
                .toList();
    }

    /** Historial de movimientos de crédito de un cadete PORCENTAJE (ronda 10, punto 94). */
    @GetMapping("/api/admin/cadetes/{id}/movimientos-credito")
    public List<MovimientoCreditoResponse> historialCredito(@PathVariable String id) {
        return service.historialCredito(id).stream()
                .map(m -> new MovimientoCreditoResponse(
                        m.getId(), m.getTipo(), m.getMonto(), m.getSaldoResultante(),
                        m.getPedidoNumero(), m.getCreadoEn(), m.getCreadoPorUsername()))
                .toList();
    }

    /** Marca al cadete como Libre a mano (todavia no existe la app que lo haga sola). */
    @PostMapping("/api/admin/cadetes/{id}/libre")
    public CadeteResponse marcarLibre(@PathVariable String id) {
        Cadete c = service.marcarLibre(id);
        publisher.publicarUbicacion(c);
        return CadeteResponse.from(c);
    }

    /** Aviso operativo a todos los cadetes conectados ahora (ej. "cerramos temprano"). */
    @PostMapping("/api/admin/cadetes/aviso")
    public void avisoGeneral(@Valid @RequestBody AvisoGeneralRequest req) {
        service.enviarAvisoGeneral(req.mensaje());
    }

    /** Últimos avisos generales enviados, con cuántos cadetes ya confirmaron haberlos visto. */
    @GetMapping("/api/admin/cadetes/avisos")
    public List<AvisoGeneralResponse> listarAvisos() {
        return service.listarAvisos();
    }

    /** Habilita a un cadete SEMANAL para trabajar esta semana, con pago completo o parcial (ronda 7). */
    @PostMapping("/api/admin/cadetes/{id}/pago-semanal/habilitar")
    public CadeteResponse habilitarPagoSemanal(@PathVariable String id, @Valid @RequestBody HabilitarPagoSemanalRequest req) {
        return CadeteResponse.from(service.habilitarPagoSemanal(id, req.montoPagado(), req.venceEn(), req.montoSemanal()));
    }

    /** Carga crédito a un cadete PORCENTAJE tras recibir su transferencia (ronda 7). */
    @PostMapping("/api/admin/cadetes/{id}/credito")
    public CadeteResponse acreditar(@PathVariable String id, @Valid @RequestBody AcreditarRequest req, Authentication auth) {
        return CadeteResponse.from(service.acreditar(id, req.monto(), auth.getName()));
    }

    /** Cambiar el modelo de cobro desde la pantalla "Pagos" unificada. */
    @PatchMapping("/api/admin/cadetes/{id}/modalidad-pago")
    public CadeteResponse cambiarModalidadPago(@PathVariable String id, @Valid @RequestBody com.cadeteria.backend.dto.CadeteDtos.ModalidadPagoRequest req) {
        return CadeteResponse.from(service.cambiarModalidadPago(id, req.modalidadPago()));
    }

    // --- Self (app de cadetes) ---

    @PatchMapping("/api/cadetes/me/estado")
    public CadeteResponse actualizarEstado(Authentication auth, @Valid @RequestBody EstadoRequest req) {
        return CadeteResponse.from(service.actualizarEstado(auth.getName(), req.estadoId()));
    }

    @PatchMapping("/api/cadetes/me/ubicacion")
    public CadeteResponse actualizarUbicacion(Authentication auth, @RequestBody UbicacionRequest req) {
        Cadete c = service.actualizarUbicacion(auth.getName(), req.lat(), req.lng());
        publisher.publicarUbicacion(c);
        return CadeteResponse.from(c);
    }

    @PatchMapping("/api/cadetes/me/fcm-token")
    public CadeteResponse actualizarFcmToken(Authentication auth, @Valid @RequestBody FcmTokenRequest req) {
        return CadeteResponse.from(service.actualizarFcmToken(auth.getName(), req.fcmToken()));
    }

    @GetMapping("/api/cadetes/me")
    public CadeteResponse me(Authentication auth) {
        return service.toResponse(service.getByUsername(auth.getName()));
    }

    /** El cadete cambia su propia contraseña desde la app. */
    @PatchMapping("/api/cadetes/me/password")
    public CadeteResponse cambiarPassword(Authentication auth, @Valid @RequestBody CambiarPasswordRequest req) {
        return CadeteResponse.from(service.cambiarPassword(auth.getName(), req.actual(), req.nueva()));
    }

    /** El cadete cambia su propio teléfono desde la app (si cambia de celular/línea). */
    @PatchMapping("/api/cadetes/me/telefono")
    public CadeteResponse actualizarTelefonoPropio(Authentication auth, @Valid @RequestBody TelefonoRequest req) {
        return CadeteResponse.from(service.actualizarTelefonoPropio(auth.getName(), req.telefono()));
    }

    /** El cadete carga sus propios datos de cobro (CBU/alias), para que el cliente le transfiera. */
    @PatchMapping("/api/cadetes/me/cuenta")
    public CadeteResponse actualizarCuenta(Authentication auth, @RequestBody CuentaRequest req) {
        return CadeteResponse.from(service.actualizarCuenta(auth.getName(), req.cbu(), req.aliasCbu()));
    }

    /** El cadete confirma que vio un aviso general (spec: registrar quién lo vio). */
    /** Avisos generales que todavía no vio — para no perderse los que llegaron mientras estaba desconectado (ronda 10, punto 98). */
    @GetMapping("/api/cadetes/me/avisos/pendientes")
    public List<AvisoGeneralResponse> avisosPendientes(Authentication auth) {
        return service.avisosPendientesDe(auth.getName());
    }

    @PostMapping("/api/cadetes/me/avisos/{id}/leido")
    public void marcarAvisoLeido(@PathVariable String id, Authentication auth) {
        service.marcarAvisoLeido(auth.getName(), id);
    }

    /** Cuánto facturó el cadete en la semana en curso y si ya se le pagó (spec 5.4, pago_semanal). */
    @GetMapping("/api/cadetes/me/pago-semanal")
    public MiSemanaResponse miPagoSemanal(Authentication auth) {
        return pagoSemanalService.miSemanaActual(auth.getName());
    }

    /** Subconjunto de /api/admin/configuracion que la app necesita (frecuencia de ubicación, Cloudinary). */
    @GetMapping("/api/cadetes/me/configuracion")
    public CadeteConfigResponse configuracion() {
        Map<String, String> valores = configuracionService.findAll();
        int frecuencia = configuracionService.getInt("frecuencia_ubicacion_seg", 45);
        int versionMinima = configuracionService.getInt("version_minima_app", 1);
        boolean firmaObligatoria = configuracionService.getBoolean("firma_receptor_obligatoria", false);
        return new CadeteConfigResponse(
                frecuencia,
                valores.getOrDefault("cloudinary_cloud_name", ""),
                valores.getOrDefault("cloudinary_upload_preset", ""),
                versionMinima,
                firmaObligatoria,
                configuracionService.getBigDecimal("pago_semanal_monto", java.math.BigDecimal.valueOf(5000)),
                configuracionService.getBigDecimal("comision_porcentaje", java.math.BigDecimal.TEN),
                configuracionService.getBigDecimal("credito_bajo_alerta_umbral", java.math.BigDecimal.valueOf(500)),
                configuracionService.getInt("tiempo_limite_aceptacion_seg", 120),
                valores.getOrDefault("telefono_soporte", ""),
                configuracionService.getBoolean("checklist_documentacion_obligatorio", false)
        );
    }

    /** Historial de avisos generales, con si este cadete ya lo vio (mejora 2026-09-16) — a diferencia de "pendientes", incluye los ya leídos. */
    @GetMapping("/api/cadetes/me/avisos/historial")
    public List<com.cadeteria.backend.dto.CadeteDtos.AvisoGeneralHistorialResponse> historialAvisos(Authentication auth) {
        return service.historialAvisosDe(auth.getName());
    }
}
