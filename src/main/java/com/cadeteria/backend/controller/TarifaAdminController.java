package com.cadeteria.backend.controller;

import com.cadeteria.backend.service.TarifaSimulacionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Herramientas de Configuración → Tarifas (panel admin). */
@RestController
@RequestMapping("/api/admin/tarifas")
public class TarifaAdminController {

    private final TarifaSimulacionService simulacionService;

    public TarifaAdminController(TarifaSimulacionService simulacionService) {
        this.simulacionService = simulacionService;
    }

    /**
     * Compara la fórmula (línea recta × factor) contra lo cobrado en los pedidos finalizados de los
     * últimos `dias`. Sin `factor` usa el configurado. Ver {@link TarifaSimulacionService}.
     */
    @GetMapping("/simular")
    public TarifaSimulacionService.Resultado simular(@RequestParam(required = false) Double factor,
                                                     @RequestParam(defaultValue = "90") int dias) {
        return simulacionService.simular(factor, dias);
    }
}
