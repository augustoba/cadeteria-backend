package com.cadeteria.backend.controller;

import com.cadeteria.backend.config.AppProperties;
import com.cadeteria.backend.repository.PedidoRepository;
import com.cadeteria.backend.service.EmailService;
import com.cadeteria.backend.service.FcmService;
import com.cadeteria.backend.service.PedidoService;
import com.cadeteria.backend.service.WebPushService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * Panel de salud del sistema (mejora 48) — para que el admin vea de un vistazo si algo
 * dejó de funcionar (SMS, push, mail, base) sin tener que mirar los logs del servidor.
 */
@RestController
@RequestMapping("/api/admin/salud")
public class SaludController {

    private final PedidoRepository pedidoRepo;
    private final PedidoService pedidoService;
    private final FcmService fcmService;
    private final EmailService emailService;
    private final WebPushService webPushService;
    private final AppProperties props;

    public SaludController(PedidoRepository pedidoRepo, PedidoService pedidoService, FcmService fcmService,
                            EmailService emailService, WebPushService webPushService, AppProperties props) {
        this.pedidoRepo = pedidoRepo;
        this.pedidoService = pedidoService;
        this.fcmService = fcmService;
        this.emailService = emailService;
        this.webPushService = webPushService;
        this.props = props;
    }

    public record SaludResponse(
            boolean dbOk,
            boolean smsGatewayConfigurado,
            boolean pushConfigurado,
            boolean emailConfigurado,
            boolean webPushConfigurado,
            long smsFallidosPendientes,
            long pedidosActivos,
            Instant ultimoPedidoCreadoEn,
            Instant consultadoEn
    ) {}

    @GetMapping
    public SaludResponse salud() {
        boolean dbOk;
        long pedidosActivos;
        Instant ultimoPedido = null;
        try {
            pedidosActivos = pedidoService.listarActivos().size();
            ultimoPedido = pedidoRepo.findFirstByOrderByCreadoEnDesc().map(p -> p.getCreadoEn()).orElse(null);
            dbOk = true;
        } catch (Exception e) {
            dbOk = false;
            pedidosActivos = 0;
        }

        boolean smsOk = props.getSms().isEnabled() && !props.getSms().getGatewayUrl().isBlank();

        return new SaludResponse(
                dbOk,
                smsOk,
                fcmService.isHabilitado(),
                emailService.isHabilitado(),
                webPushService.isHabilitado(),
                dbOk ? pedidoService.contarSmsFallidos() : 0,
                pedidosActivos,
                ultimoPedido,
                Instant.now()
        );
    }
}
