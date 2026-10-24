package com.cadeteria.backend.controller;

import com.cadeteria.backend.dto.ClienteDtos.ClienteAvisoResponse;
import com.cadeteria.backend.dto.ClienteDtos.ClienteFichaResponse;
import com.cadeteria.backend.dto.ClienteDtos.ClienteRequest;
import com.cadeteria.backend.dto.ClienteDtos.ClienteResponse;
import com.cadeteria.backend.dto.ClienteDtos.ClientesPaginaResponse;
import com.cadeteria.backend.dto.ClienteDtos.LiquidarCuentaCorrienteResponse;
import com.cadeteria.backend.dto.ClienteDtos.ReporteClienteResponse;
import com.cadeteria.backend.service.ClienteService;
import com.cadeteria.backend.service.ReporteClienteService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/** Vista de Clientes ABM + ficha/historial por teléfono (ronda 4, puntos 44, 58, 67). */
@RestController
@RequestMapping("/api/admin/clientes")
public class ClienteAdminController {

    private final ClienteService service;
    private final ReporteClienteService reporteClienteService;

    public ClienteAdminController(ClienteService service, ReporteClienteService reporteClienteService) {
        this.service = service;
        this.reporteClienteService = reporteClienteService;
    }

    @GetMapping
    public ClientesPaginaResponse listar(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano) {
        var r = service.listarPaginado(q, pagina, tamano);
        return new ClientesPaginaResponse(r.items(), r.total(), r.pagina(), r.totalPaginas());
    }

    /** Aviso rápido al cargar un pedido nuevo — si el teléfono es problemático o tiene tarifa especial. */
    @GetMapping("/aviso")
    public ClienteAvisoResponse aviso(@RequestParam String telefono) {
        return service.aviso(telefono);
    }

    /** Reportes de cadetes sobre este teléfono, del más nuevo al más viejo (spec-antiabuso Fase 4). */
    @GetMapping("/{telefono}/reportes")
    public java.util.List<ReporteClienteResponse> reportes(@PathVariable String telefono) {
        return reporteClienteService.deTelefono(telefono).stream().map(ReporteClienteResponse::from).toList();
    }

    @GetMapping("/{telefono}")
    public ClienteFichaResponse ficha(@PathVariable String telefono) {
        return service.ficha(telefono);
    }

    @PutMapping("/{telefono}")
    public ClienteResponse guardar(@PathVariable String telefono, @Valid @RequestBody ClienteRequest req) {
        ClienteRequest normalizado = new ClienteRequest(
                telefono, req.nombreContacto(), req.empresa(), req.tarifaEspecial(),
                req.problematico(), req.notasProblematico(), req.activo(), req.modalidadFacturacion());
        return service.guardar(normalizado);
    }

    /** Corregir un teléfono mal cargado (2026-10-24): todo el cliente se muda al número nuevo. */
    @PostMapping("/{telefono}/cambiar-telefono")
    public ClienteResponse cambiarTelefono(@PathVariable String telefono,
                                           @Valid @RequestBody com.cadeteria.backend.dto.ClienteDtos.CambiarTelefonoRequest req,
                                           org.springframework.security.core.Authentication auth) {
        return service.cambiarTelefono(telefono, req.telefonoNuevo(), auth.getName());
    }

    @GetMapping("/{telefono}/cambios-telefono")
    public java.util.List<com.cadeteria.backend.dto.ClienteDtos.TelefonoCambioResponse> cambiosTelefono(@PathVariable String telefono) {
        return service.cambiosDeTelefono(telefono).stream()
                .map(com.cadeteria.backend.dto.ClienteDtos.TelefonoCambioResponse::from).toList();
    }

    /** Cierra el período de cuenta corriente (ronda 10, punto 100). */
    @PostMapping("/{telefono}/liquidar-cuenta-corriente")
    public LiquidarCuentaCorrienteResponse liquidarCuentaCorriente(@PathVariable String telefono) {
        return service.liquidarCuentaCorriente(telefono);
    }
}
