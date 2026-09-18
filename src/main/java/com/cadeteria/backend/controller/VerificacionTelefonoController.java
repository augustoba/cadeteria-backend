package com.cadeteria.backend.controller;

import com.cadeteria.backend.dto.VerificacionTelefonoDtos.EnviarCodigoRequest;
import com.cadeteria.backend.dto.VerificacionTelefonoDtos.VerificarCodigoRequest;
import com.cadeteria.backend.dto.VerificacionTelefonoDtos.VerificarCodigoResponse;
import com.cadeteria.backend.service.VerificacionTelefonoService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Confirma el teléfono de quien carga un pedido en "/pedir" sin login (ver VerificacionTelefonoService). */
@RestController
@RequestMapping("/api/publico/verificacion-telefono")
public class VerificacionTelefonoController {

    private final VerificacionTelefonoService service;

    public VerificacionTelefonoController(VerificacionTelefonoService service) {
        this.service = service;
    }

    @PostMapping("/enviar")
    public void enviar(@Valid @RequestBody EnviarCodigoRequest req) {
        service.enviarCodigo(req.telefono());
    }

    @PostMapping("/verificar")
    public VerificarCodigoResponse verificar(@Valid @RequestBody VerificarCodigoRequest req) {
        return new VerificarCodigoResponse(service.verificarCodigo(req.telefono(), req.codigo()));
    }
}
