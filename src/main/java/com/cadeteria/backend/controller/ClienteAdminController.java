package com.cadeteria.backend.controller;

import com.cadeteria.backend.dto.ClienteDtos.ClienteAvisoResponse;
import com.cadeteria.backend.dto.ClienteDtos.ClienteFichaResponse;
import com.cadeteria.backend.dto.ClienteDtos.ClienteRequest;
import com.cadeteria.backend.dto.ClienteDtos.ClienteResponse;
import com.cadeteria.backend.dto.ClienteDtos.ClientesPaginaResponse;
import com.cadeteria.backend.dto.ClienteDtos.LiquidarCuentaCorrienteResponse;
import com.cadeteria.backend.service.ClienteService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/** Vista de Clientes ABM + ficha/historial por teléfono (ronda 4, puntos 44, 58, 67). */
@RestController
@RequestMapping("/api/admin/clientes")
public class ClienteAdminController {

    private final ClienteService service;

    public ClienteAdminController(ClienteService service) {
        this.service = service;
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

    /** Cierra el período de cuenta corriente (ronda 10, punto 100). */
    @PostMapping("/{telefono}/liquidar-cuenta-corriente")
    public LiquidarCuentaCorrienteResponse liquidarCuentaCorriente(@PathVariable String telefono) {
        return service.liquidarCuentaCorriente(telefono);
    }
}
