package com.cadeteria.backend.controller;

import com.cadeteria.backend.dto.ConfiguracionDtos.ConfiguracionResponse;
import com.cadeteria.backend.dto.ConfiguracionDtos.ConfiguracionUpdateRequest;
import com.cadeteria.backend.service.ConfiguracionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/configuracion")
public class ConfiguracionController {

    private final ConfiguracionService service;

    public ConfiguracionController(ConfiguracionService service) {
        this.service = service;
    }

    @GetMapping
    public ConfiguracionResponse get() {
        return new ConfiguracionResponse(service.findAll());
    }

    @PutMapping
    public ConfiguracionResponse set(@Valid @RequestBody ConfiguracionUpdateRequest req) {
        service.set(req.clave(), req.valor());
        return new ConfiguracionResponse(service.findAll());
    }
}
