package com.cadeteria.backend.controller;

import com.cadeteria.backend.dto.LookupResponse;
import com.cadeteria.backend.dto.SolicitudCadeteDtos.SolicitudFormRequest;
import com.cadeteria.backend.dto.SolicitudCadeteDtos.TokenEstadoResponse;
import com.cadeteria.backend.model.Lookup;
import com.cadeteria.backend.repository.TipoVehiculoRepository;
import com.cadeteria.backend.service.SolicitudCadeteService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Formulario público de alta de cadete (ronda 7) — sin autenticación, el token del link
 * es lo único que lo protege y es de un solo uso.
 */
@RestController
@RequestMapping("/api/publico/solicitudes-cadete")
public class SolicitudCadetePublicoController {

    private final SolicitudCadeteService service;
    private final TipoVehiculoRepository tipoVehiculoRepo;

    public SolicitudCadetePublicoController(SolicitudCadeteService service, TipoVehiculoRepository tipoVehiculoRepo) {
        this.service = service;
        this.tipoVehiculoRepo = tipoVehiculoRepo;
    }

    /** Para el combo de tipo de vehículo del formulario público. */
    @GetMapping("/tipos-vehiculo")
    public List<LookupResponse> tiposVehiculo() {
        return tipoVehiculoRepo.findAll().stream().map((Lookup l) -> LookupResponse.from(l)).toList();
    }

    /** El front la usa antes de mostrar el formulario, para avisar si el link ya no sirve. */
    @GetMapping("/{token}")
    public TokenEstadoResponse validar(@PathVariable String token) {
        try {
            return new TokenEstadoResponse(true, null, service.correccionDe(service.validarToken(token)));
        } catch (RuntimeException e) {
            return new TokenEstadoResponse(false, e.getMessage(), null);
        }
    }

    @PostMapping("/{token}")
    public void enviar(@PathVariable String token, @Valid @RequestBody SolicitudFormRequest req) {
        service.enviarFormulario(token, req);
    }
}
