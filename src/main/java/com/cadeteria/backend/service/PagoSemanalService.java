package com.cadeteria.backend.service;

import com.cadeteria.backend.dto.PagoSemanalDtos.MiSemanaResponse;
import com.cadeteria.backend.dto.PagoSemanalDtos.PagoRequest;
import com.cadeteria.backend.model.Admin;
import com.cadeteria.backend.model.Cadete;
import com.cadeteria.backend.model.PagoSemanal;
import com.cadeteria.backend.model.Pedido;
import com.cadeteria.backend.repository.AdminRepository;
import com.cadeteria.backend.repository.PagoSemanalRepository;
import com.cadeteria.backend.repository.PedidoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/** Registro manual del pago semanal por cadete (spec 5.4) — sin integracion con medios de pago. */
@Service
@Transactional
public class PagoSemanalService {

    private static final ZoneId ZONA_ART = ZoneId.of("America/Argentina/Buenos_Aires");

    private final PagoSemanalRepository repo;
    private final CadeteService cadeteService;
    private final AdminRepository adminRepo;
    private final PedidoRepository pedidoRepo;

    public PagoSemanalService(PagoSemanalRepository repo, CadeteService cadeteService, AdminRepository adminRepo,
                               PedidoRepository pedidoRepo) {
        this.repo = repo;
        this.cadeteService = cadeteService;
        this.adminRepo = adminRepo;
        this.pedidoRepo = pedidoRepo;
    }

    @Transactional(readOnly = true)
    public List<PagoSemanal> listar(String cadeteId) {
        return repo.findByCadeteIdOrderBySemanaInicioDesc(cadeteId);
    }

    /**
     * Cadetes activos sin un pago registrado (pagado=true) para la última semana ya
     * cerrada — recordatorio para el admin (ronda 6, punto 35).
     */
    @Transactional(readOnly = true)
    public List<Cadete> cadetesConPagoPendiente() {
        LocalDate lunesSemanaAnterior = LocalDate.now(ZONA_ART).with(DayOfWeek.MONDAY).minusWeeks(1);
        return cadeteService.findAll().stream()
                .filter(Cadete::isActivo)
                .filter(c -> !repo.findByCadeteIdAndSemanaInicio(c.getId(), lunesSemanaAnterior)
                        .map(PagoSemanal::isPagado).orElse(false))
                .toList();
    }

    public PagoSemanal registrar(String cadeteId, PagoRequest req, String adminUsername) {
        Cadete cadete = cadeteService.get(cadeteId);
        Admin admin = adminRepo.findByUsername(adminUsername).orElse(null);
        PagoSemanal pago = repo.findByCadeteIdAndSemanaInicio(cadeteId, req.semanaInicio()).orElseGet(() -> {
            PagoSemanal nuevo = new PagoSemanal();
            nuevo.setId(UUID.randomUUID().toString());
            nuevo.setCadete(cadete);
            nuevo.setSemanaInicio(req.semanaInicio());
            return nuevo;
        });
        pago.setPagado(req.pagado());
        pago.setRegistradoPor(admin);
        pago.setRegistradoEn(Instant.now());
        return repo.save(pago);
    }

    /** Para la app: cuánto facturó el cadete en la semana en curso (lunes a ahora) y si ya se le pagó. */
    @Transactional(readOnly = true)
    public MiSemanaResponse miSemanaActual(String cadeteUsername) {
        Cadete cadete = cadeteService.getByUsername(cadeteUsername);
        LocalDate lunes = LocalDate.now(ZONA_ART).with(DayOfWeek.MONDAY);
        Instant desde = lunes.atStartOfDay(ZONA_ART).toInstant();
        Instant hasta = Instant.now();
        List<Pedido> finalizados = pedidoRepo
                .findByCadeteAsignadoIdAndEstadoIdAndFinalizadoEnBetweenOrderByFinalizadoEnDesc(cadete.getId(), "FINALIZADO", desde, hasta);
        BigDecimal facturado = finalizados.stream().map(Pedido::getPrecio).reduce(BigDecimal.ZERO, BigDecimal::add);
        boolean pagado = repo.findByCadeteIdAndSemanaInicio(cadete.getId(), lunes).map(PagoSemanal::isPagado).orElse(false);
        return new MiSemanaResponse(lunes, facturado, finalizados.size(), pagado);
    }
}
