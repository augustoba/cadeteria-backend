package com.cadeteria.backend.controller;

import com.cadeteria.backend.dto.WhatsappDtos.AckRequest;
import com.cadeteria.backend.dto.WhatsappDtos.ChipResponse;
import com.cadeteria.backend.dto.WhatsappDtos.ChipsEstadoRequest;
import com.cadeteria.backend.dto.WhatsappDtos.EntregaRequest;
import com.cadeteria.backend.dto.WhatsappDtos.EstadoGatewayResponse;
import com.cadeteria.backend.dto.WhatsappDtos.MensajesPaginaResponse;
import com.cadeteria.backend.dto.WhatsappDtos.PairingCodigoRequest;
import com.cadeteria.backend.dto.WhatsappDtos.RespuestaEntranteRequest;
import com.cadeteria.backend.dto.WhatsappDtos.RespuestasPaginaResponse;
import com.cadeteria.backend.dto.WhatsappDtos.TestEnviarRequest;
import com.cadeteria.backend.dto.WhatsappDtos.VincularChipRequest;
import com.cadeteria.backend.service.WhatsappGatewayService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/**
 * Gateway propio de WhatsApp (Baileys + chips descartables). Los @MessageMapping los
 * consume el gateway (Node) autenticado con el token compartido (ver
 * WhatsappGatewayAuthInterceptor); los endpoints REST bajo /api/admin/whatsapp son para
 * el panel (probar el envío a mano, cargar chips nuevos y ver el estado de todo).
 */
@Controller
public class WhatsappController {

    private final WhatsappGatewayService service;

    public WhatsappController(WhatsappGatewayService service) {
        this.service = service;
    }

    @MessageMapping("/whatsapp/ack")
    public void ack(AckRequest req) {
        service.confirmar(req.mensajeId(), req.ok(), req.chipUsado(), req.error(), req.waMessageId());
    }

    @MessageMapping("/whatsapp/entrega")
    public void entrega(EntregaRequest req) {
        service.registrarEntrega(req.mensajeId(), req.tipo());
    }

    @MessageMapping("/whatsapp/respuesta")
    public void respuesta(RespuestaEntranteRequest req) {
        service.registrarRespuesta(req.chipId(), req.telefono(), req.texto());
    }

    @MessageMapping("/whatsapp/chips-estado")
    public void chipsEstado(ChipsEstadoRequest req) {
        service.actualizarEstadoChips(req.chips());
    }

    @MessageMapping("/whatsapp/pairing-codigo")
    public void pairingCodigo(PairingCodigoRequest req) {
        service.guardarPairingCodigo(req.chipId(), req.codigo());
    }

    @RestController
    @RequestMapping("/api/admin/whatsapp")
    public static class Admin {

        private final WhatsappGatewayService service;

        public Admin(WhatsappGatewayService service) {
            this.service = service;
        }

        @GetMapping("/estado")
        public EstadoGatewayResponse estado() {
            return service.estado();
        }

        /** Prueba manual desde el panel, sin pedido asociado. */
        @PostMapping("/test")
        public ResponseEntity<Void> test(@Valid @RequestBody TestEnviarRequest req) {
            service.enviar(req.telefono(), req.mensaje(), null);
            return ResponseEntity.accepted().build();
        }

        @GetMapping("/chips")
        public List<ChipResponse> chips() {
            return service.chips();
        }

        /** Da de alta un chip nuevo y le pide al gateway el pairing code para vincularlo. */
        @PostMapping("/chips")
        public ChipResponse vincularChip(@Valid @RequestBody VincularChipRequest req) {
            return service.vincularChip(req.chipId(), req.numero());
        }

        /** El chip se rompió o no se usa más — le pide al gateway que cierre sesión y libere el número. */
        @DeleteMapping("/chips/{chipId}")
        public ResponseEntity<Void> darDeBajaChip(@PathVariable String chipId) {
            service.darDeBajaChip(chipId);
            return ResponseEntity.noContent().build();
        }

        /** Borra el registro para siempre — solo si ya está BAJA (ver WhatsappGatewayService.eliminarChip). */
        @DeleteMapping("/chips/{chipId}/registro")
        public ResponseEntity<Void> eliminarChip(@PathVariable String chipId) {
            service.eliminarChip(chipId);
            return ResponseEntity.noContent().build();
        }

        /** Paginado + filtro por teléfono y rango de fechas (mejora: con 200+ mensajes/día, todo junto no escala). */
        @GetMapping("/mensajes")
        public MensajesPaginaResponse mensajes(
                @RequestParam(required = false) String telefono,
                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant desde,
                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant hasta,
                @RequestParam(defaultValue = "0") int pagina,
                @RequestParam(defaultValue = "25") int tamano) {
            return service.mensajes(telefono, desde, hasta, pagina, tamano);
        }

        @GetMapping("/respuestas")
        public RespuestasPaginaResponse respuestas(
                @RequestParam(required = false) String telefono,
                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant desde,
                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant hasta,
                @RequestParam(defaultValue = "0") int pagina,
                @RequestParam(defaultValue = "25") int tamano) {
            return service.respuestas(telefono, desde, hasta, pagina, tamano);
        }
    }
}
